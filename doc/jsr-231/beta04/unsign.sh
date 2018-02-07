
PREFIX="jogl-"
temp_dir="~tmp"
unsigned_dir="unsigned"
for fisier in `ls -1`; do
	if [ ${fisier:0:${#PREFIX}} = $PREFIX ]; then
		rm -rf $temp_dir
		mkdir -p $temp_dir
		mkdir -p $unsigned_dir
		echo "	<< unarchiving "$fisier" >>"
		unzip -qq $fisier -d $temp_dir
		rm -rf $temp_dir/META-INF
		echo "	<< creating unsigned "$fisier" >>"
		cd $temp_dir
		jar cf ../$unsigned_dir/$fisier *
		cd ..
		rm -rf $temp_dir
	fi
done

