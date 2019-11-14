#!/usr/bin/env bash

./exec.sh "mkdir -p /opt/tidb/bin"
wget -O /tmp/unistore.tar.gz "http://fileserver.pingcap.net/download/unistore.tar.gz"
tar -zxvf /tmp/unistore.tar.gz
./update-binary.sh pd-server
./update-binary.sh unistore-server
./update-binary.sh tidb-server
