package com.aegisflow.simulator.warehouse;

import com.aegisflow.simulator.warehouse.reservation.*;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

/** Explicit local CLI only. No HTTP endpoint, Core writes, clock override or remote recovery. */
final class LocalDemo {
    private LocalDemo() { }
    static String argument(String[] args,String name) {
        var values=java.util.Arrays.stream(args).filter(a -> a.startsWith("--aegisflow.demo."+name+"="))
                .map(a -> a.substring(a.indexOf('=')+1)).toList();
        if(values.size()!=1) throw new IllegalArgumentException("Exactly one demo "+name+" required");
        return values.getFirst();
    }
    static void run(String[] args) {
        UUID run=UUID.fromString(argument(args,"run"));
        String scenario=argument(args,"scenario");
        String mode=argument(args,"mode");
        validate(scenario,mode);
        String tenant="demo-"+run;
        String tx=scenario+"-"+run;
        UUID order=UUID.nameUUIDFromBytes(("order:"+tx).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID resource=UUID.nameUUIDFromBytes(("warehouse:"+tx).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var app=new SpringApplication(WarehouseSimulatorApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        // Command-line values outrank inherited container flags; this CLI never starts a relay.
        var launch=new java.util.ArrayList<String>(java.util.Arrays.asList(args));
        launch.add("--spring.main.web-application-type=none");
        launch.add("--spring.profiles.active=postgres");
        launch.add("--aegisflow.warehouse.relay.enabled=false");
        launch.add("--spring.flyway.enabled=false");
        try(var context=app.run(launch.toArray(String[]::new))) {
            if (java.util.Arrays.asList(args).contains("--aegisflow.demo.barrier=true")) {
                awaitBarrier(java.nio.file.Path.of("/tmp/aegisflow-demo-" + run + "-" + scenario),
                        java.time.Duration.ofSeconds(180));
            }
            UUID event=execute(context,tenant,tx,order,resource,mode);
            System.out.println("DEMO_RESULT owner=warehouse tenant="+tenant+" transaction="+tx+" event="+event);
        }
    }
    static void awaitBarrier(java.nio.file.Path prefix, java.time.Duration timeout) {
        var ready = prefix.resolveSibling(prefix.getFileName() + ".ready");
        var go = prefix.resolveSibling(prefix.getFileName() + ".go");
        try {
            java.nio.file.Files.createFile(ready);
            long end = System.nanoTime() + timeout.toNanos();
            while (!java.nio.file.Files.exists(go)) {
                if (System.nanoTime() >= end) throw new IllegalStateException("Demo readiness barrier timed out");
                Thread.sleep(50);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Demo readiness barrier interrupted", interrupted);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot establish demo readiness barrier", failure);
        }
    }
    static void validate(String scenario,String mode) {
        if(!java.util.Set.of("healthy","missing").contains(scenario)) throw new IllegalArgumentException("Unknown demo scenario");
        if(!java.util.Set.of("create","hold","release").contains(mode)) throw new IllegalArgumentException("Unknown demo operation");
        if(!mode.equals("create") && !scenario.equals("missing")) throw new IllegalArgumentException("Only missing scenario may hold/release");
    }
    static UUID execute(org.springframework.context.ConfigurableApplicationContext context,
            String tenant,String tx,UUID order,UUID resource,String mode) {
        var service=context.getBean(ReservationApplicationService.class);
            var jdbc=context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
            var transaction=new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            return transaction.execute(status -> {
                if (mode.equals("release")) {
                    var event=jdbc.queryForObject("SELECT event_id FROM aegisflow.outbox_event WHERE tenant_id=? AND aggregate_id=? AND aggregate_version=1", UUID.class,tenant,resource);
                    jdbc.update("UPDATE aegisflow.outbox_event SET next_attempt_at=clock_timestamp() WHERE tenant_id=? AND event_id=? AND status='PENDING'",tenant,event);
                    return event;
                }
                var result=service.createReservation(new CreateReservationCommand(tenant,tx,resource,tx,order.toString(),tx,
                        ReservationStatus.RESERVED,null,java.util.List.of(new ReservationLine("sku-1",2))));
                if (mode.equals("hold")) {
                    int updated=jdbc.update("UPDATE aegisflow.outbox_event SET next_attempt_at=clock_timestamp()+interval '1 day' WHERE tenant_id=? AND event_id=? AND status='PENDING'",tenant,result.eventId());
                    if(updated!=1) throw new IllegalStateException("Cannot hold an already delivered event");
                }
                return result.eventId();
            });
    }
}
