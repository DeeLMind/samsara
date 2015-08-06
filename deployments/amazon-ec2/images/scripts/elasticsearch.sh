#!/bin/bash -e

if [ "$(id -u)" != "0" ] ; then
    echo "Running the script with root privilege's"
    sudo "$0"
    exit $?
fi

echo "waiting for system to fully come online."
sleep 30


echo '------------------------------------------------------------------'
echo '                    Setup upstart service'
echo '------------------------------------------------------------------'
cat >/etc/init/elasticsearch.conf <<\EOF
description "ElasticSearch container"
author "Bruno"
start on runlevel [2345]
stop on runlevel [016]
respawn
pre-start exec /usr/bin/docker rm els | true
exec /usr/bin/docker run --name els \
       -p 9200:9200 \
       -p 9300:9300 \
       -p 15000:15000 \
       -v /logs/els:/logs \
       -v /data/els:/data \
       -e "ADV_IP=$(curl 'http://169.254.169.254/latest/meta-data/local-ipv4')" \
       -e "ZOOKEEPER_PORT_2181_TCP_ADDR=10.10.1.5" \
       -e "ZOOKEEPER_PORT_2181_TCP_PORT=2181" \
       samsara/elasticsearch

pre-stop script
        /usr/bin/docker stop els
        /usr/bin/docker rm els
end script
# TODO:
#       -e "ZOOKEEPER1_PORT_2181_TCP=tcp://10.10.1.5:2181" \
#       -e "ZOOKEEPER2_PORT_2181_TCP=tcp://10.10.2.5:2181" \
#       -e "ZOOKEEPER3_PORT_2181_TCP=tcp://10.10.3.5:2181" \
EOF

echo '------------------------------------------------------------------'
echo '                Pull the latest image'
echo '------------------------------------------------------------------'
docker pull samsara/elasticsearch
