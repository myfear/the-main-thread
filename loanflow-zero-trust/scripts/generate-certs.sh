#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CERTS="${ROOT}/infrastructure/certs"
PASSWORD="${CERTS_PASSWORD:-changeit}"
DAYS=825

mkdir -p "${CERTS}/ca"

if [[ ! -f "${CERTS}/ca/ca.key" ]]; then
  openssl genrsa -out "${CERTS}/ca/ca.key" 4096
  openssl req -x509 -new -nodes -key "${CERTS}/ca/ca.key" -sha256 -days "${DAYS}" \
    -out "${CERTS}/ca/ca.crt" \
    -subj "/CN=LoanFlow Local CA/O=The Main Thread/C=DE"
fi

create_service_cert() {
  local service="$1"
  local dir="${CERTS}/${service}"
  mkdir -p "${dir}"

  if [[ -f "${dir}/keystore.p12" ]]; then
  echo "Skipping ${service} (keystore already exists)"
    return
  fi

  openssl genrsa -out "${dir}/tls.key" 2048
  openssl req -new -key "${dir}/tls.key" \
    -out "${dir}/tls.csr" \
    -subj "/CN=${service}/O=LoanFlow/C=DE"

  openssl x509 -req -in "${dir}/tls.csr" \
    -CA "${CERTS}/ca/ca.crt" -CAkey "${CERTS}/ca/ca.key" -CAcreateserial \
    -out "${dir}/tls.crt" -days "${DAYS}" -sha256 \
    -extfile <(printf "subjectAltName=DNS:localhost,IP:127.0.0.1")

  openssl pkcs12 -export \
    -inkey "${dir}/tls.key" \
    -in "${dir}/tls.crt" \
    -certfile "${CERTS}/ca/ca.crt" \
    -out "${dir}/keystore.p12" \
  -name "${service}" \
    -passout "pass:${PASSWORD}"

  rm -f "${dir}/tls.csr"
  echo "Created ${service} certificate"
}

create_service_cert loan-service
create_service_cert credit-service
create_service_cert document-service

if [[ ! -f "${CERTS}/truststore.p12" ]]; then
  keytool -importcert -noprompt \
    -alias loanflow-ca \
    -file "${CERTS}/ca/ca.crt" \
    -keystore "${CERTS}/truststore.p12" \
    -storepass "${PASSWORD}" \
    -storetype PKCS12
  echo "Created truststore"
fi

echo "Certificates ready under ${CERTS}"
