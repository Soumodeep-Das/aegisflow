#!/bin/sh
set -eu
[ -r /tmp/aegisflow-kafka/admin.properties ] || exit 1
export KAFKA_HEAP_OPTS='-Xms64m -Xmx128m'
# Topic listing must complete an authenticated metadata request.
# BrokerApiVersions prints some failures while returning zero, so it is not used.
exec /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server 127.0.0.1:9092 \
    --command-config /tmp/aegisflow-kafka/admin.properties --list >/dev/null 2>&1
