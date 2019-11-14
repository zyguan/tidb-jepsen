#! /bin/sh

for test in "bank-multitable" "register" "append"
do
    for nemesis in "kill-kv" "kill-pd"
        do
	        lein run test --workload ${test} --nemesis ${nemesis} --time-limit 60 --concurrency 2n --txn-mode "optimistic"
            if [ $? -ne 0 ]
            then
                echo ${test} ${nemesis}
                exit 1
            fi
	    sleep 15
    done
done
