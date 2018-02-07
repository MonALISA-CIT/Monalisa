BEGIN {
    getline;
    split($0, args, " ");

    for (i in args)
	if (i%2==0)
	    replace[(i-2)/2] = args[i];
	else
	    find[(i-1)/2] = args[i];
}

{
    for (i in find)
	gsub(find[i], replace[i]);
    
    print $0;
}
