#!/usr/bin/env bash


# Installs a server supporting physical robots as well as simulated robots without laser simulation.
#
# If you simply want to install the server product at the default location, use this command:
#
#   "./install.sh"
#
# If you want to change the installation directory, you should use:
#
#   "INSTALLATION_DIRECTORY_PREFIX=/your/preferred/directory ./install.sh"

# Check if container tool is installed
if command -v "docker" &> /dev/null; then
    tool="docker"
elif command -v "podman" &> /dev/null; then
    tool="podman"
else
    echo "Error: No container tool found. Please install docker or podman before running this script."
    exit 1
fi

echo "Use $tool to start the service."

# Compose command validation
if ! $tool compose version &>/dev/null; then
    echo "Error: Compose command not found for $tool!"
    exit 1
fi

CONTAINER_TOOL="$tool"
TARGET_FILE="$HOME/.profile"

# Delete old line if exist
sudo sed -i '/CONTAINER_TOOL=/d' "$TARGET_FILE" 2>/dev/null
sudo sed -i '/^CONTAINER_TOOL=/d' /etc/environment 2>/dev/null

# Write to user environment
echo "export CONTAINER_TOOL=\"$CONTAINER_TOOL\"" | sudo tee -a "$TARGET_FILE" >/dev/null
# Write to system environment
echo "CONTAINER_TOOL=$tool" | sudo tee -a /etc/environment

source $TARGET_FILE
source /etc/environment

INSTALLATION_DIRECTORY_PREFIX=${INSTALLATION_DIRECTORY_PREFIX:-/opt}

INSTALLATION_SOURCE=server
VERSION_INFO_SOURCE=info
INSTALLATION_DIRECTORY=${INSTALLATION_DIRECTORY_PREFIX}/server

# print all commands for better debugging
set -x

# immediately exit on error
set -e

if [ -f "$INSTALLATION_DIRECTORY/server-services.sh" ]; then
  echo "------ uninstalling existing server..."
  bash $INSTALLATION_DIRECTORY/server-services.sh uninstall
fi

echo "------ copying server files to $INSTALLATION_DIRECTORY..."
# make sure to include hidden files (.env) on copy
# see https://superuser.com/a/367303
mkdir -p $INSTALLATION_DIRECTORY
cp -r $INSTALLATION_SOURCE/. $INSTALLATION_DIRECTORY/
# copy info folder to installation directory
cp -r $VERSION_INFO_SOURCE $INSTALLATION_DIRECTORY/

# Get the current server timezone
CURRENT_TIME_ZONE=$(timedatectl | grep "Time zone" | awk '{print $3}')
echo "------ current server time zone: $CURRENT_TIME_ZONE"

# Update .env file with the current server timezone
if [ -f "$INSTALLATION_DIRECTORY/.env" ]; then
  echo "------ updating TIME_ZONE in .env file..."
  sed -i "s|^TIME_ZONE=.*|TIME_ZONE=$CURRENT_TIME_ZONE|" $INSTALLATION_DIRECTORY/.env
else
  echo "TIME_ZONE=$CURRENT_TIME_ZONE" >> $INSTALLATION_DIRECTORY/.env
fi

echo "------ loading install registry..."
$CONTAINER_TOOL load -i docker/install-registry.tar

echo "------ starting install registry..."
$CONTAINER_TOOL run -d \
  -p 5001:5000 \
  --restart=always \
  --name install-registry \
  install-registry:latest

echo "------ changing directory to $INSTALLATION_DIRECTORY..."
cd $INSTALLATION_DIRECTORY

echo "------ calling ./set-vars-in-config.sh ..."
bash ./set-vars-in-config.sh $INSTALLATION_DIRECTORY ${@}

echo "------ starting server..."
bash ./server-services.sh start

echo "Installation of server successful."
