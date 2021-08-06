#!/bin/bash

rm -f *.ks
rm -f *.ts

keytool -noprompt -genkey -alias localhost -keystore selfsigned.ks -storepass password -deststoretype pkcs12 -validity 3650 -keyalg EC -keysize 256 -sigalg SHA256WithECDSA -dname "CN=localhost" -ext san=ip:127.0.0.1
keytool -noprompt -export -alias localhost -keystore selfsigned.ks -storepass password -file selfsigned.cer
keytool -noprompt -importcert -trustcacerts -file selfsigned.cer -keystore selfsigned.ts -alias localhost_cert -storepass password -deststoretype pkcs12

cp selfsigned.ks broker.ks
cp selfsigned.ks client.ks
cp selfsigned.ts client.ts
cp selfsigned.ts broker.ts

#keytool -noprompt -genkey -alias broker -keystore broker.ks -storepass password -deststoretype pkcs12 -validity 3650 -keyalg EC -keysize 256 -sigalg SHA256WithECDSA -dname "CN=localhost" -ext san=ip:127.0.0.1
#keytool -noprompt -export -alias broker -keystore broker.ks -storepass password -file broker.cer
#keytool -noprompt -importcert -trustcacerts -file broker.cer -keystore client.ts -alias broker_cert -storepass password -deststoretype pkcs12

#keytool -noprompt -genkey -alias client -keystore client.ks -storepass password -deststoretype pkcs12 -validity 3650 -keyalg EC -keysize 256 -sigalg SHA256WithECDSA -dname "CN=localhost" -ext san=ip:127.0.0.1
#keytool -noprompt -export -alias client -keystore client.ks -storepass password -file client.cer
#keytool -noprompt -importcert -trustcacerts -file client.cer -keystore broker.ts -alias client_cert -storepass password -deststoretype pkcs12

