#!/usr/bin/env bash

set -u
bash "$@"
result=$?
if (( result != 0 )); then
  echo "Node dung voi exit code $result. Xem loi o tren."
  read -r -p "Nhan Enter de dong terminal..." || true
fi
exit "$result"
