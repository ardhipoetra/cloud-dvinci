#!/bin/bash
rm file
while true
 do
 myarr=($(onevm list | awk '{print $8}'))
 len=${#myarr[@]}
 #len2=len-1
 #len3=((len-1))
 
 let len=len-1
 for i in `seq 1 $len`
  do
        echo $i>>file.log
	date>>file.log
	sh proc.sh ${myarr[i]}>>file.log
 done
#sleep 1
done

