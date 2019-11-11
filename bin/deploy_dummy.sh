#!/usr/bin/env bash

function clean_up {
    printf "\nCleaning up...\n"
    rm -rf "${1}"
}

function error_exit {
    printf "\n${1:-\"Unknown Error\"}\n" 1>&2
    exit 1
}

printf "=====================================\n"
printf "|        Demo  Deployment            |\n"
printf "**This is a demo deployment example**\n"
printf "=====================================\n"

DIR="$(pwd)"
file_path="${DIR}/src"
file_name="test.txt"
full_filename="${file_path}/${file_name}"

mkdir -p ${file_path}
echo "$(date +%s)" > $full_filename

# Clean up
clean_up "${file_path}"
printf "\nCompleted!\n"

exit 0
