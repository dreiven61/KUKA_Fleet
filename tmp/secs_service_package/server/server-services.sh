#!/usr/bin/env bash
# starts/stops/uninstalls all services of an installed robot
# for server installation, refer to install.sh coming with the product

# usage: 
# server-services.sh [start|stop|restart|uninstall]

TARGET_FILE="$HOME/.profile"
source $TARGET_FILE

if [[ -z $CONTAINER_TOOL ]]; then
    if command -v "docker" &> /dev/null; then
        tool="docker"
    elif command -v "podman" &> /dev/null; then
        tool="podman"
    else
        echo "Error: No container tool found. Please install docker or podman before running this script."
        exit 1
    fi

    CONTAINER_TOOL="$tool"
    TARGET_FILE="$HOME/.profile"

    # Delete old line if exist
    sudo sed -i '/CONTAINER_TOOL=/d' "$TARGET_FILE" 2>/dev/null

    # Write to user environment
    echo "export CONTAINER_TOOL=\"$CONTAINER_TOOL\"" | sudo tee -a "$TARGET_FILE" >/dev/null
    # Write to system environment
    echo "CONTAINER_TOOL=$tool" | sudo tee -a /etc/environment

    source $TARGET_FILE
    source /etc/environment
fi

INSTALLATION_DIRECTORY_PREFIX=${INSTALLATION_DIRECTORY_PREFIX:-/opt}

INSTALLATION_DIRECTORY=${INSTALLATION_DIRECTORY_PREFIX}/server
FINAL_REGISTRY_PORT=5001
FINAL_REGISTRY_PATH=localhost:${FINAL_REGISTRY_PORT}

ymlParams="-f $INSTALLATION_DIRECTORY/docker-compose.yml"

warning_message="WARNING! Uninstalling the server software will:\na) delete all databases and\nb) forcefully delete directory '$INSTALLATION_DIRECTORY' .\nPlease create a backup of any important data.\nDo you want to continue? (yes/no): "

show_warning_before_uninstall() {
    while true; do
        read -p "$(echo -e $warning_message)" choice
        case "$choice" in
            yes ) break;;
            no ) echo "Aborting uninstallation."; exit 0;;
            * ) echo "Please type 'yes' or 'no'.";;
        esac
    done
}

# search for *.override.yml in the installation directory
readarray -d '' ymlFiles < <(find $INSTALLATION_DIRECTORY -maxdepth 1 -type f \( -iname "*server.override.yml" \) -print0)
for f in "${ymlFiles[@]}"
do
	ymlParams="${ymlParams} -f ${f}"
done

if [[ "$1" == "start" ]]
then
	# immediately exit on error
	set -e

	echo "------ starting server..."
	echo "environment params:"
	cat $INSTALLATION_DIRECTORY/.env
	echo -e "\n----------------"
	echo "creating missing .env folders"
	$INSTALLATION_DIRECTORY/create-env-folders.sh $INSTALLATION_DIRECTORY/.env
	echo -e "\n----------------"
	echo "$CONTAINER_TOOL compose ${ymlParams} up -d"
	$CONTAINER_TOOL compose ${ymlParams} up -d

	echo "------ starting installed simulated robots (if any)..."
	$INSTALLATION_DIRECTORY/simulation/sim-services.sh all start

elif [[ "$1" == "restart" ]]
then
	echo "------ restarting server..."
    $INSTALLATION_DIRECTORY/server-services.sh stop
    $INSTALLATION_DIRECTORY/server-services.sh start

elif [[ "$1" == "stop" ]]
then
	echo "------ stopping installed simulated robots (if any)..."
    	$INSTALLATION_DIRECTORY/simulation/sim-services.sh all stop

	echo "------ stopping server..."
	echo "$CONTAINER_TOOL compose ${ymlParams} down"
	$CONTAINER_TOOL compose ${ymlParams} down

elif [[ "$1" == "uninstall" ]]
then
  show_warning_before_uninstall

	echo "------ removing install-registry"
	$CONTAINER_TOOL stop install-registry
	$CONTAINER_TOOL rm --volumes install-registry
	$CONTAINER_TOOL rmi --force install-registry

	echo "------ uninstalling simulated robots..."
	$INSTALLATION_DIRECTORY/simulation/sim-services.sh all uninstall

	echo "------ uninstalling old server..."
	echo "$CONTAINER_TOOL compose ${ymlParams} down --volumes"
	$CONTAINER_TOOL compose ${ymlParams} down --volumes

	echo "------ deleting server config ..."
	# deleting the whole installation folder, to make sure we also get rid of hidden files within (.env), which a
	# `rm -rf dir/*` would keep.
	# see https://unix.stackexchange.com/a/77196
	rm -rf $INSTALLATION_DIRECTORY

	echo "------ removing ${FINAL_REGISTRY_PATH}/* $CONTAINER_TOOL images..."
	$CONTAINER_TOOL rmi -f $($CONTAINER_TOOL images -q --filter=reference="${FINAL_REGISTRY_PATH}/*")

	echo "------ Delete the previously added environment parameters..."
	sudo sed -i '/CONTAINER_TOOL=/d' "$TARGET_FILE" 2>/dev/null
	sudo sed -i '/^CONTAINER_TOOL=/d' /etc/environment 2>/dev/null

	cd $HOME
	source $TARGET_FILE
	source /etc/environment
	echo "Uninstallation complete."
else
	echo "Error: unknown mode \"$1\""
	echo "usage: server-services.sh [start|stop|restart|uninstall]"
	exit 1
fi
