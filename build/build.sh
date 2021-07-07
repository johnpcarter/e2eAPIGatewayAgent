#!/bin/sh

#!/bin/sh

while [ "$1" != "" ]; do
	case $1 in
    	-c | --clean )           
            clean="true"
            ;;
        -t | --target )
			shift
			target=$1
            ;;
        -cya| --c8y_app_name )
			shift
			c8y_app_name=$1
            ;;
        -cyu| --c8y_user )
			shift
			cumulocity_user=$1
            ;;
        -cyp| --c8y_pwd )
			shift
			cumulocity_pwd=$1
            ;;
        -cyh| --c8y_url )
			shift
			cumulocity_url=$1
            ;;
        -cyt| --c8y_tenant )
			shift
			cumulocity_tenant=$1
            ;;
    	* )   
			echo "invalid argument '$1'"
            exit 1
	esac
	shift
done

if [ "x${target}" == 'x' ]
then
	target=jcart/microservices:api-e2e-demo-0.1
fi

if [ "x${clean}" != 'x' ]
then
	echo "Downloading latest sources from https://github.com/johnpcarter/wm.git"

	git clone https://github.com/johnpcarter/wm.git
	rm -Rf source
	mv wm source
fi

echo "Building ${target}"

docker build -t ${target} .

if [ "x${c8y_app_name}" != "x" ]
then

	echo "Preparing Cumulocity microservice ${c8y_app_name}"

	rm image.tar
	rm ${c8y_app_name}.zip
	
	echo "Generating zip file with docker image and cumulocity.json def"

	docker save -o image.tar ${target}
	cp resources/cumulocity.json .
	zip ${c8y_app_name} cumulocity.json image.tar

	echo "Uploading new zip ${c8y_app_name}.zip to ${cumulocity_url}"

	./resources/cumulocity-microservice.sh deploy -n ${c8y_app_name} -u ${cumulocity_user} -p ${cumulocity_pwd} -d ${cumulocity_url} -te ${cumulocity_tenant}
fi