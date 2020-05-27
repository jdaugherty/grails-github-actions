#!/bin/bash
# $1 == GH_TOKEN

set -e

echo "Configuring git"
git config --global user.email "${GITHUB_ACTOR}@users.noreply.github.com"
git config --global user.name "${GITHUB_ACTOR}"
cd micronaut-core
projectName="${GITHUB_REPOSITORY:19}"
git checkout -b "$projectName-$2"

if [ ! -z $bomProperty ]; 
then
    echo "name: $bomProperty"
    echo "value: ${!bomProperty}"
    sed -i "s/^$bomProperty.*$/$bomProperty\=${projectVersion}/" gradle.properties
fi

if [ ! -z $bomProperties ]; 
then
    IFS=','
    for property in $bomProperties
    do
        echo "name: $property"
        echo "value: ${!property}"
        sed -i "s/^$property.*$/$property\=${!property}/" gradle.properties
    done
fi

echo "Changes Applied"
git diff

if [ -z $DRY_RUN ]
then
    echo "Creating pull request"
    git add gradle.properties
    git commit -m "Bump $projectName to $2"
    git push origin "$projectName-$2"
    pr_url=`curl -s --request POST -H "Authorization: Bearer $1" -H "Content-Type: application/json" https://api.github.com/repos/micronaut-projects/micronaut-core/pulls --data "{\"title\": \"Bump $projectName to $2\", \"head\":\"$projectName-$2\", \"base\":\"$githubCoreBranch\"}" | jq '.url'`
    curl -i --request PATCH -H "Authorization: Bearer $1" -H "Content-Type: application/json" $pr_url --data "{\"labels\": [\"type: dependency-upgrade\"]}"
fi