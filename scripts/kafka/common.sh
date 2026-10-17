#!/bin/sh
# Only fixed client identities and restricted secrets enter generated Java properties.
# Do not enable shell tracing: these functions handle local credentials.

fail() {
    printf '%s\n' "$1" >&2
    exit 1
}

validate_password() {
    case "$2" in
        ''|*[!0-9a-f]*) fail "$1 must be 64 lowercase hexadecimal characters." ;;
    esac
    [ "${#2}" -eq 64 ] || fail "$1 must be 64 lowercase hexadecimal characters."
}

prepare_private_directory() {
    umask 077
    mkdir -p /tmp/aegisflow-kafka/logs
    chmod 700 /tmp/aegisflow-kafka
}

write_admin_properties() {
    validate_password KAFKA_ADMIN_PASSWORD "${KAFKA_ADMIN_PASSWORD-}"
    # The password alphabet cannot inject newlines, quotes or Java-property escapes.
    {
        printf 'bootstrap.servers=%s\n' "$1"
        printf '%s\n' 'security.protocol=SASL_PLAINTEXT' 'sasl.mechanism=PLAIN'
        printf 'sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="admin" password="%s";\n' "$KAFKA_ADMIN_PASSWORD"
        printf '%s\n' 'request.timeout.ms=5000' 'default.api.timeout.ms=10000'
    } > /tmp/aegisflow-kafka/admin.properties
    chmod 600 /tmp/aegisflow-kafka/admin.properties
}
