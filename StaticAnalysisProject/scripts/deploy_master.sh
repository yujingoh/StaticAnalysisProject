servers=( "172.20.134.145")
#fsids=( "fs4" "fs5" "fs6" )

for server in ${servers[@]}
do 
    echo deploying zip file to $server 
    ssh user@$server mkdir ~/master
    scp ~/deploydir/master.zip user@$server:~/master.zip 
    ssh user@$server unzip -o ~/master.zip -d ~/master
    ssh user@$server chmod +x  ~/master/*.sh
done

echo starting slicestore id ${fsids[i]} on ${servers[i]} 
ssh user@${servers[i]} 'cd ~/master;./start_svdsm.sh  >> /home/user/a.out 2>&1 &'
 
 
