package com.aegisflow.simulator.order;

import com.aegisflow.simulator.order.persistence.*;
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
        UUID resource=UUID.nameUUIDFromBytes(("order:"+tx).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var app=new SpringApplication(OrderSimulatorApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        // Command-line values outrank inherited container flags; this CLI never starts a relay.
        var launch=new java.util.ArrayList<String>(java.util.Arrays.asList(args));
        launch.add("--spring.main.web-application-type=none");
        launch.add("--spring.profiles.active=postgres");
        launch.add("--aegisflow.order.relay.enabled=false");
        launch.add("--spring.flyway.enabled=false");
        try(var context=app.run(launch.toArray(String[]::new))) {
            if (java.util.Arrays.asList(args).contains("--aegisflow.demo.barrier=true")) {
                awaitBarrier(java.nio.file.Path.of("/tmp/aegisflow-demo-" + run + "-" + scenario),
                        java.time.Duration.ofSeconds(180));
            }
            UUID event=execute(context,tenant,tx,order,resource,mode);
            System.out.println("DEMO_RESULT owner=order tenant="+tenant+" transaction="+tx+" event="+event);
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
        if(!java.util.Set.of("create").contains(mode)) throw new IllegalArgumentException("Unknown demo operation");
        
    }
    private static UUID execute(org.springframework.context.ConfigurableApplicationContext context,
            String tenant,String tx,UUID order,UUID resource,String mode) {
        return context.getBean(OrderPersistenceService.class).create(new CreateOrder(tenant, order, tx, tx, tx,
                    new java.math.BigDecimal("10.00"), "USD", java.util.List.of(new CreateOrder.Line("sku-1",2)))).eventId();
    }
}
