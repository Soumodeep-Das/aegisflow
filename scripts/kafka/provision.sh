#!/bin/sh
set -eu
. /opt/aegisflow/kafka/common.sh
prepare_private_directory
write_admin_properties kafka:9092

# Do not alter existing topics or remove ACLs. Repeat bootstrap is additive;
# the verification client checks topic configuration and selected ACL boundaries.
for owner in order payment warehouse; do
    topic="aegisflow.$owner.events.v1"
    /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 \
        --command-config /tmp/aegisflow-kafka/admin.properties \
        --create --if-not-exists --topic "$topic" --partitions 3 --replication-factor 1 \
        --config min.insync.replicas=1 --config retention.ms=604800000 \
        --config cleanup.policy=delete --config max.message.bytes=1048576
    /opt/kafka/bin/kafka-acls.sh --bootstrap-server kafka:9092 \
        --command-config /tmp/aegisflow-kafka/admin.properties \
        --add --allow-principal "User:$owner" --operation Write --operation Describe --topic "$topic"
    /opt/kafka/bin/kafka-acls.sh --bootstrap-server kafka:9092 \
        --command-config /tmp/aegisflow-kafka/admin.properties \
        --add --allow-principal "User:$owner" --operation IdempotentWrite --cluster
    /opt/kafka/bin/kafka-acls.sh --bootstrap-server kafka:9092 \
        --command-config /tmp/aegisflow-kafka/admin.properties \
        --add --allow-principal User:core --operation Read --operation Describe --topic "$topic"
done
/opt/kafka/bin/kafka-acls.sh --bootstrap-server kafka:9092 \
    --command-config /tmp/aegisflow-kafka/admin.properties \
    --add --allow-principal User:core --operation Read --operation Describe \
    --group aegisflow-core- --resource-pattern-type prefixed
printf '%s\n' 'Kafka topic provisioning and source/Core ACL bootstrap completed.'
