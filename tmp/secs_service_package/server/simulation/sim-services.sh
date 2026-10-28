#!/usr/bin/env bash

# Installs, starts, stops, or uninstalls all components of a simulated robot.
# Upon installation, additional parameters for the respective simulated robot can be given.
#
# For usage see end of file.

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

ROBOT_TEMPLATE_DIRECTORY=${INSTALLATION_DIRECTORY_PREFIX}/server/simulation
SERVER_INSTALLATION_DIRECTORY=${INSTALLATION_DIRECTORY_PREFIX}/server

save_compose_profile() {
    # the .env file to update
    local env_file="$1/.env"

    # the new entry to add
    local used_profile="$2"

    if [ -f "$env_file" ]; then
        # Update the .env file with the new value
        sed -i "s/^COMPOSE_PROFILES=.*/COMPOSE_PROFILES=$used_profile/" "$env_file"
        echo "Updated COMPOSE_PROFILES with: $used_profile"
    else
        echo "file not found: $env_file"
    fi
}

# print all commands for better debugging
set -x

if [[ "$1" == "all" ]]
then
    if [ "$#" -lt 2 ] || [ 3 -lt "$#" ]; then
	    echo "Wrong number of arguments! Usage: sim-services.sh all [start|stop|restart|uninstall] [COMPOSE_PROFILE]"
	    exit 1
    fi

  	readarray -d '' simEnvFiles < <(find $INSTALLATION_DIRECTORY_PREFIX -maxdepth 1 -type d \( -iname "robot-*" \) -print0)
	for f in "${simEnvFiles[@]}"
	do
		if [[ $f =~ ($INSTALLATION_DIRECTORY_PREFIX/robot-)([^,]*) ]] && [ -f "$f/sim-services.sh" ]; then
			echo "------ found installed robot ${BASH_REMATCH[2]}"
			bash $ROBOT_TEMPLATE_DIRECTORY/sim-services.sh "$2" "${BASH_REMATCH[2]}" "$3"
		fi
	done
	exit 0
fi

if [ 2 -gt "$#" ]; then
	echo "sim-services needs at least two parameters: ./sim-services.sh [install|uninstall|start|stop] [ROBOT_UUID]"
	exit 1
fi

ROBOT_DIR=robot-${2}
ROBOT_INSTALLATION_DIRECTORY=${INSTALLATION_DIRECTORY_PREFIX}/$ROBOT_DIR

if [[ "$1" == "install" ]]
then
	if [ 3 -gt "$#" ]; then
		echo "Not all parameters [ROBOT_UUID] [ROBOT_NAME] given. Exiting."
		exit 1
	fi
    ROBOT_ID=$2
	ROBOT_NAME=$3

	# immediately exit on error
	set -e

	DIAG_PARM="DIAGNOSTICS_OUTPUT=${INSTALLATION_DIRECTORY_PREFIX}/robot-${ROBOT_ID}/diagnostics"
	LOG_PARM="LOG_HOME=${INSTALLATION_DIRECTORY_PREFIX}/robot-${ROBOT_ID}/logs"
	CONFIG_PARM="CONFIG_HOME=${INSTALLATION_DIRECTORY_PREFIX}/robot-${ROBOT_ID}/config"

	CUSTOM_PARAMS=()
	CUSTOM_PARAMS+=("ROBOT_UUID=${ROBOT_ID}")
	CUSTOM_PARAMS+=("COMPOSE_PROJECT_NAME=sim-${ROBOT_ID}")
	CUSTOM_PARAMS+=($DIAG_PARM)
	CUSTOM_PARAMS+=($LOG_PARM)
	CUSTOM_PARAMS+=($CONFIG_PARM)
	CUSTOM_PARAMS+=("ROBOT_NAME=${ROBOT_NAME}")
	CUSTOM_PARAMS+=("ROBOT_SERIAL_NO=SN:${2}-${ROBOT_NAME}")

	echo "------ creating ${ROBOT_INSTALLATION_DIRECTORY}"
	if [ -d "${ROBOT_INSTALLATION_DIRECTORY}" ];
	then
		echo "Error: robot directory ${ROBOT_INSTALLATION_DIRECTORY} already exists. Please delete it and try again."
		exit 1
	fi

	cp -r ${ROBOT_TEMPLATE_DIRECTORY} ${ROBOT_INSTALLATION_DIRECTORY}

	echo "------ saving and applying config by calling $^/set-vars-in-config.sh "
	bash $SERVER_INSTALLATION_DIRECTORY/set-vars-in-config.sh ${ROBOT_INSTALLATION_DIRECTORY} ${CUSTOM_PARAMS[@]} ${@:5}

	bash $ROBOT_TEMPLATE_DIRECTORY/sim-services.sh start $2

	echo "Installation of simulated robot ${2} successful."
	exit 0
fi

if [ ! -f "$ROBOT_INSTALLATION_DIRECTORY/sim-services.sh" ];
then
	echo "Error: file $ROBOT_INSTALLATION_DIRECTORY/sim-services.sh does not exist. Exiting."
	exit 1
fi

if [[ "$1" == "uninstall" ]]
then
	echo "------ shutting down modules of simulated robot $2, removing volumes"
	$CONTAINER_TOOL compose --env-file $ROBOT_INSTALLATION_DIRECTORY/.env -f $ROBOT_INSTALLATION_DIRECTORY/docker-compose.yml down --volumes

	echo "------ removing volumes sim-${2}-pgdata and persistence (maybe already deleted)"
	# should be have been deleted automatically, but sometimes still present
	$CONTAINER_TOOL volume rm --force sim-${2}-persistence
	$CONTAINER_TOOL volume rm --force sim-${2}-sim-persistence

	echo "------ removing robot installation directory $ROBOT_INSTALLATION_DIRECTORY"
	rm -rf $ROBOT_INSTALLATION_DIRECTORY

	echo "Uninstallation of simulated robot ${2} successful."

	exit 0

elif [[ "$1" == "start" ]]
then
  	ROBOT_ID=$2
  	COMPOSE_PROFILE=$3

  	# save the profile that was used to start the robot
  	# this way, we can also shutdown those services, when the "stop", "restart" or "uninstall" commands are called
  	save_compose_profile "$ROBOT_INSTALLATION_DIRECTORY" $COMPOSE_PROFILE

  	if [ -z "$COMPOSE_PROFILE" ]; then
	  	echo "------ starting module group of simulated robot ${ROBOT_ID} without a profile"
  	else
    	echo "------ starting module group of simulated robot ${ROBOT_ID} using profile ${COMPOSE_PROFILE} (specified in .env file)"
  	fi

	$CONTAINER_TOOL compose --env-file $ROBOT_INSTALLATION_DIRECTORY/.env -f $ROBOT_INSTALLATION_DIRECTORY/docker-compose.yml up -d

	echo "Starting of simulated robot ${ROBOT_ID} successful."
	exit 0

elif [[ "$1" == "stop" ]]
then
	echo "------ shutting down modules of simulated robot $2"
	$CONTAINER_TOOL compose --env-file $ROBOT_INSTALLATION_DIRECTORY/.env -f $ROBOT_INSTALLATION_DIRECTORY/docker-compose.yml down

	echo "Stopping of simulated robot ${2} successful."

	exit 0

elif [[ "$1" == "restart" ]]
then
  ROBOT_ID=$2
  COMPOSE_PROFILE=$3

	echo "------ restarting robot $ROBOT_ID"
	$ROBOT_TEMPLATE_DIRECTORY/sim-services.sh stop $ROBOT_ID
	$ROBOT_TEMPLATE_DIRECTORY/sim-services.sh start $ROBOT_ID $COMPOSE_PROFILE
	exit 0
fi

echo "Error: unknown parameter $1 given."
echo "Usage ([var1=val1 var2=val2 ...] are optional):"
echo "- to install: sim-services.sh install ROBOT_UUID ROBOT_NAME [var1=val1 var2=val2 ...]."
echo "- to start or restart: sim-services.sh [start|restart] ROBOT_UUID [COMPOSE_PROFILE]"
echo "- to stop or uninstall: sim-services.sh [stop|uninstall] ROBOT_UUID"
echo "- to apply a command to all installed robots: sim-services.sh all [start|stop|restart|uninstall] [COMPOSE_PROFILE]"
