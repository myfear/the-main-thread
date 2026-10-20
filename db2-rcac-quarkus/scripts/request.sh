#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
load_env
[[ $# -eq 2 ]] || die 'Usage: request.sh alice|bob|auditor|unmapped /customers[/ID]'
user=$1
request_path=$2
case "$user" in
    alice) password=$HTTP_ALICE_PASSWORD ;;
    bob) password=$HTTP_BOB_PASSWORD ;;
    auditor) password=$HTTP_AUDITOR_PASSWORD ;;
    unmapped) password=$HTTP_UNMAPPED_PASSWORD ;;
    *) die 'Unknown local HTTP user.' ;;
esac
[[ "$request_path" == /customers || "$request_path" == /customers/* || "$request_path" == /customers\?* ]] || die 'Only /customers requests are accepted.'
curl --silent --show-error --user "$user:$password" --url "http://localhost:8080$request_path" \
    --write-out '\nHTTP %{http_code}\n'
