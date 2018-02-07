<script language=JavaScript>
    var imgResolutions = '<<:resolutions js:>>';
    var imgResDefault  = '<<:defaultres js:>>';
    
    function displayImgResOptions(obj){
	if (obj.options){
	    var res = imgResolutions.split(',');
	    
	    var i = 0;
	    
	    while (i<res.length){
		obj.options[i] = new Option(res[i], res[i]);
		
		if (res[i] == imgResDefault)
		    obj.options[i].selected = true;
		    
		i++;
	    }
	}
    }
</script>

<<:continut:>>
