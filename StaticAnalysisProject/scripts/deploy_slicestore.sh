servers=( "172.20.134.151" "172.20.134.152" "172.20.134.153")
fsids=( "fs4" "fs5" "fs6" )

for server in ${servers[@]}
do 
    echo deploying zip file to $server 
    ssh user@$server mkdir ~/slicestore
    ssh user@$server mkdir ~/slicestore/FS
    scp ~/deploydir/slicestore.zip user@$server:~/slicestore.zip 
    ssh user@$server unzip -o ~/slicestore.zip -d ~/slicestore
    ssh user@$server chmod +x  ~/slicestore/*.sh
done


for ((i=0;i<3;i++))  
do 
     echo starting slicestore id ${fsids[i]} on ${servers[i]} 
     ssh user@${servers[i]} 'cd ~/slicestore;./start_svdss.sh ${fsids[i]} ${servers[i]} >> /home/user/a.out 2>&1 &'
     
done
 
