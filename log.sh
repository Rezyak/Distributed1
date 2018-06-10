#!/bin/bash
logdir='./logs'

print(){
    for file in $(ls -t ${logdir})
    do
        echo -e "====>\t${file}\t<===="
        tail ${logdir}/${file}
        echo -e "\n"
    done
}

print