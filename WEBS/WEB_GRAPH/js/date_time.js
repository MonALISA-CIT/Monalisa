function quickjump(){
    alert("quickjump");

    index = document.form1.quickint.selectedIndex;
    
    value = document.form1.quickint[index].value;
	
    intmin = document.getElementById('intmin');
	    
    if (intmin.length>value)
        intmin.selectedIndex = intmin.length - value - 1;
    else
        intmin.selectedIndex = 0;
		
    intmax = document.getElementById('intmax');
		    
    intmax.selectedIndex = intmax.length - 1;
			
    modify();
}

var cal1 = new calendar1(document.forms['form1'].elements['interval_date_low']);
var cal2 = new calendar1(document.forms['form1'].elements['interval_date_high']);
	    
cal1.year_scroll = false;
cal1.time_comp = false;

cal2.year_scroll = false;
cal2.time_comp = false;

for (i=0;i<24;i++){
    document.form1.interval_hour_low.options[i]=new Option((i<10?"0":"")+i+":00",i);;
    document.form1.interval_hour_high.options[i]=new Option((i<10?"0":"")+i+":00",i);;
}
	    
init_form();

function init_form(){
    var dmin = new Date(now.getTime()-document.forms['form1'].elements['interval.min'].value);
    var dmax = new Date(now.getTime()-document.forms['form1'].elements['interval.max'].value);
    
    document.form1.interval_hour_low.selectedIndex = dmin.getHours();
    document.form1.interval_hour_high.selectedIndex = dmax.getHours();
    
    document.form1.interval_date_low.value=dmin.getDate()+"-"+(dmin.getMonth()+1)+"-"+dmin.getFullYear();
    document.form1.interval_date_high.value=dmax.getDate()+"-"+(dmax.getMonth()+1)+"-"+dmax.getFullYear();

    update_quick_jump();	
}
	    
function update_quick_jump(){
    var found = false;
		
    if (document.forms['form1'].elements['interval.max'].value==0)
	for (i=1; i<document.form1.quick_interval.options.length; i++)
	    if (document.form1.quick_interval.options[i].value==document.forms['form1'].elements['interval.min'].value){
	        document.form1.quick_interval.selectedIndex=i;
	        found=true;
	    }
			
    if (!found)
        document.form1.quick_interval.selectedIndex=0;
}
	    
function quick_jump(){
    var val = document.form1.quick_interval.options[document.form1.quick_interval.selectedIndex].value;
		
    if (val>0){
        document.forms['form1'].elements['interval.min'].value=val;
        document.forms['form1'].elements['interval.max'].value=0;
		
        init_form();
    }
		
    document.form1.submit();
}
	    
function recalc(){
    var arr = document.form1.interval_date_low.value.split('-');
	    
    var sd1 = 
        arr[1]+"/"+arr[0]+"/"+arr[2]+" "+
        document.form1.interval_hour_low.options[document.form1.interval_hour_low.selectedIndex].value+":00:00";
		    
    var d1 = new Date(sd1);
		
    var diff = now.getTime() - d1.getTime();
		
    if (diff<3600000){
        init_form();
    }
    else{
        document.forms['form1'].elements['interval.min'].value=diff;
    }

    arr = document.form1.interval_date_high.value.split('-');
	    
    var sd2 = 
        arr[1]+"/"+arr[0]+"/"+arr[2]+" "+
        document.form1.interval_hour_high.options[document.form1.interval_hour_high.selectedIndex].value+":00:00";
		    
    var d2 = new Date(sd2);
	
    diff = now.getTime() - d2.getTime();
		
    if (diff<0){
        init_form();
    }
    else{
        document.forms['form1'].elements['interval.max'].value=diff;
    }
		
    update_quick_jump();
}
	    
function move_back(){
    var diff = document.forms['form1'].elements['interval.max'].value - document.forms['form1'].elements['interval.min'].value;
		
    if (diff<0)
        diff=-diff;

    if (diff<3600000)
        diff=3600000;
		    
    var x = document.forms['form1'].elements['interval.max'].value;
    document.forms['form1'].elements['interval.max'].value = eval(x+"+"+diff);
	
    var y = document.forms['form1'].elements['interval.min'].value;
    document.forms['form1'].elements['interval.min'].value = eval(y+"+"+diff);

    init_form();
    update_quick_jump();
		
    document.form1.submit();	
}

function move_next(){
    var diff = document.forms['form1'].elements['interval.max'].value - document.forms['form1'].elements['interval.min'].value;
		
    if (diff<0)
        diff=-diff;
		    
    if (diff<3600000)
        diff=3600000;
		    
    var x = document.forms['form1'].elements['interval.max'].value;
    x = eval(x+"-"+diff);
    if (x<0) x=0;
    document.forms['form1'].elements['interval.max'].value = x;
		
    var y = document.forms['form1'].elements['interval.min'].value;
    y = eval(y+"-"+diff);
    if (y<diff) y=diff;
    document.forms['form1'].elements['interval.min'].value = y;

    init_form();
    update_quick_jump();
		
    document.form1.submit();
}
