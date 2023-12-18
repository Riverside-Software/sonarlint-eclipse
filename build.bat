set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.5.8-hotspot
set JAVA_HOME_11_X64=C:\Program Files\Eclipse Adoptium\jdk-11.0.13.8-hotspot
set JAVA_HOME_17_X64=C:\Program Files\Eclipse Adoptium\jdk-17.0.5.8-hotspot

mvn --toolchains .cirrus/toolchains.xml -Dmaven.test.skip=true -DARCHITECT_P2_DIR=/C:/Progress/P2 clean package
