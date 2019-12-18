#!/bin/sh

set -e

GRADLEPATH=""

# Read command line arguments
for i in "$@"
do
case $i in
    -p=*|--path=*)
    GRADLEPATH="${i#*=}"
    shift # past argument=value
    ;;
    -h*|--help*)
    echo " -h / --help  : print this menu"
    echo " -p=\"custom/path\" / --path=\"custom/path\"  : set custom install path for gradle; default is /opt/gradle"
    exit
    ;;
    *)
          # unknown option
    ;;
esac
done

# Install Java

if [ -f /etc/debian_version ]; then
    for package in openjdk-8-jdk wget unzip; do
        if [ "$(dpkg --status -- $package 2>/dev/null|sed -n 's/^Status: //p')" != "install ok installed" ]; then
            # add a space after old values
            missing="${missing:+$missing }$package"
        fi
    done
    if [ -n "$missing" ]; then
        echo "$0: missing required DEB packages. Installing via sudo." 1>&2
        sudo apt-get -y install $missing
    fi
elif [ -f /etc/fedora-release ]; then
    for package in java-1.8.0-openjdk wget unzip; do
        if [ "$(rpm -qa $package 2>/dev/null)" == "" ]; then
            missing="${missing:+$missing }$package"
        fi
    done
    if [ -n "$missing" ]; then
        echo "$0: missing required RPM packages. Installing via sudo." 1>&2
        sudo yum -y install $missing
    fi
elif [ -f /etc/redhat-release ]; then
    for package in java-1.8.0-openjdk wget unzip; do
        if [ "$(rpm -qa $package 2>/dev/null)" == "" ]; then
            missing="${missing:+$missing }$package"
        fi
    done
    if [ -n "$missing" ]; then
        echo "$0: missing required RPM packages. Installing via sudo." 1>&2
        sudo yum -y install $missing
    fi
fi

# Download and install Gradle

if [ -z "$GRADLEPATH" ]; then 
    GRADLEPATH="/opt/gradle"
fi

echo " Gradle will be installed under $GRADLEPATH"

# The version is hardcoded on purpose in order to match the 
# one used for testing in the Ceph test framework Teuthology
version=6.0.1

if [ ! -d /opt/gradle ]; then
    sudo mkdir ${GRADLEPATH}
fi
wget https://services.gradle.org/distributions/gradle-$version-bin.zip
sudo unzip -o -d ${GRADLEPATH} gradle-$version-bin.zip
rm -rf gradle-$version-bin.zip*

# make a version-independent symlink
ln -s gradle-$version ${GRADLEPATH}/gradle

echo "export PATH=${GRADLEPATH}/gradle-$version/bin:$PATH"
export PATH=${GRADLEPATH}/gradle-$version/bin:$PATH
gradle -v
