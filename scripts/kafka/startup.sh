#!/bin/sh
set -eu
. /opt/aegisflow/kafka/common.sh

prepare_private_directory
seen_passwords=' '
for credential_name in BROKER ADMIN ORDER PAYMENT WAREHOUSE CORE; do
    credential_value=$(printenv "KAFKA_${credential_name}_PASSWORD") || fail "Missing Kafka credential."
    validate_password "KAFKA_${credential_name}_PASSWORD" "$credential_value"
    case "$seen_passwords" in
        *" $credential_value "*) fail 'Kafka principals must use distinct credentials.' ;;
    esac
    seen_passwords="$seen_passwords$credential_value "
done
unset credential_name credential_value seen_passwords

case "${KAFKA_CLUSTER_ID-}" in
    ''|*[!A-Za-z0-9_-]*) fail 'KAFKA_CLUSTER_ID must be a 22-character Kafka UUID.' ;;
esac
[ "${#KAFKA_CLUSTER_ID}" -eq 22 ] || fail 'KAFKA_CLUSTER_ID must be a 22-character Kafka UUID.'
case "${KAFKA_HOST_PORT-}" in
    ''|*[!0-9]*) fail 'KAFKA_HOST_PORT must be a port from 1024 through 65535.' ;;
esac
[ "${#KAFKA_HOST_PORT}" -le 5 ] && [ "$KAFKA_HOST_PORT" -ge 1024 ] && [ "$KAFKA_HOST_PORT" -le 65535 ] || fail 'KAFKA_HOST_PORT must be a port from 1024 through 65535.'

server_config=/tmp/aegisflow-kafka/server.properties
cp /opt/aegisflow/kafka/server.properties "$server_config"
{
    printf 'advertised.listeners=INTERNAL://kafka:9092,HOST://127.0.0.1:%s\n' "$KAFKA_HOST_PORT"
    # Controller/inter-broker authentication never falls back to anonymous access.
    printf 'listener.name.controller.plain.sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="broker" password="%s" user_broker="%s";\n' "$KAFKA_BROKER_PASSWORD" "$KAFKA_BROKER_PASSWORD"
    for listener_name in internal host; do
        printf 'listener.name.%s.plain.sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="broker" password="%s"' "$listener_name" "$KAFKA_BROKER_PASSWORD"
        printf ' user_broker="%s" user_admin="%s" user_order="%s" user_payment="%s" user_warehouse="%s" user_core="%s";\n' "$KAFKA_BROKER_PASSWORD" "$KAFKA_ADMIN_PASSWORD" "$KAFKA_ORDER_PASSWORD" "$KAFKA_PAYMENT_PASSWORD" "$KAFKA_WAREHOUSE_PASSWORD" "$KAFKA_CORE_PASSWORD"
    done
} >> "$server_config"
chmod 600 "$server_config"
write_admin_properties 127.0.0.1:9092

# --ignore-formatted retains storage. Reject accidental identity changes before it
# can skip an existing metadata file; never delete or reinitialize retained data.
if [ -f /var/lib/kafka/data/meta.properties ]; then
    stored_cluster_id=$(sed -n 's/^cluster.id=//p' /var/lib/kafka/data/meta.properties)
    stored_node_id=$(sed -n 's/^node.id=//p' /var/lib/kafka/data/meta.properties)
    [ "$stored_cluster_id" = "$KAFKA_CLUSTER_ID" ] && [ "$stored_node_id" = 1 ] || fail 'Retained Kafka identity differs from configured cluster/node; preserve the volume and resolve configuration.'
fi
/opt/kafka/bin/kafka-storage.sh format --ignore-formatted --cluster-id "$KAFKA_CLUSTER_ID" --config "$server_config"
exec /opt/kafka/bin/kafka-server-start.sh "$server_config"
