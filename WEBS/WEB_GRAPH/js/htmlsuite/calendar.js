    //time limit
    var myCalendarModel = new DHTMLSuite.calendarModel({ initialYear:2004,initialMonth:5,initialDay:20 });
    var dNow = new Date();
    
    dNow.setDate(dNow.getDate()+1);
    
    //limita de sus
    myCalendarModel.addInvalidDateRange({year: dNow.getFullYear(), month: dNow.getMonth()+1, day: dNow.getDate()}, false);
    //limita de jos
    //myCalendarModel.addInvalidDateRange({year: 2005,month:1,day:1},false);
    
    var calendarObjForFormMin = new DHTMLSuite.calendar({minuteDropDownInterval:60, 
						    numberOfRowsInHourDropDown:10, 
						    callbackFunctionOnDayClick:'getDateFromCalendarMin', 
						    isDragable:true, 
						    displayTimeBar:true});

    var calendarObjForFormMax = new DHTMLSuite.calendar({minuteDropDownInterval:60,
						    numberOfRowsInHourDropDown:10,
						    callbackFunctionOnDayClick:'getDateFromCalendarMax', 
						    isDragable:true, 
						    displayTimeBar:true});

    calendarObjForFormMax.setCalendarModelReference(myCalendarModel);
    calendarObjForFormMin.setCalendarModelReference(myCalendarModel);
    
    calendarObjForFormMax.setCallbackFunctionOnClose('myOtherFunctionMax');
    calendarObjForFormMin.setCallbackFunctionOnClose('myOtherFunctionMin');
    
    function setCalendarFineGranularity(){
	calendarObjForFormMin.minuteDropDownInterval = 5;
	calendarObjForFormMax.minuteDropDownInterval = 5;
    }

    function myOtherFunctionMax(inputArray){
	getDateFromCalendarCloseMax(inputArray);
	update_quick_jump();
    }

    function myOtherFunctionMin(inputArray){
	getDateFromCalendarCloseMin(inputArray);
	update_quick_jump();
    }

    function pickDate(buttonObj, inputObject, calendarObjForForm){
	if(calendarObjForForm.isVisible()){
	    calendarObjForForm.hide();
	}
	else{
	    calendarObjForForm.setCalendarPositionByHTMLElement(inputObject, 0, inputObject.offsetHeight+2);	// Position the calendar right below the form input
	    calendarObjForForm.setInitialDateFromInput(inputObject, 'yyyy-mm-dd hh:ii');	// Specify that the calendar should set it's initial date from the value of the input field.
	    calendarObjForForm.addHtmlElementReference('myDate', inputObject);	// Adding a reference to this element so that I can pick it up in the getDateFromCalendar below(myInput is a unique key)
	    calendarObjForForm.resetViewDisplayedMonth();	// This line resets the view back to the inital display, i.e. it displays the inital month and not the month it displayed the last time it was open.
	    calendarObjForForm.display();
	}
    }
    
    /* inputArray is an associative array with the properties
    year
    month
    day
    hour
    minute
    calendarRef - Reference to the DHTMLSuite.calendar object.
    */
    function getDateFromCalendarClose(inputArray, references){
	var sDate = inputArray.year + '-' + inputArray.month + '-' + inputArray.day + ' ' + inputArray.hour + ':' + inputArray.minute;
	references.myDate.value = sDate;
	
	//alert(sDate);
	
	var dDate = new Date();
	dDate.setFullYear(inputArray.year, inputArray.month-1, inputArray.day);
	dDate.setHours(inputArray.hour);
	dDate.setMinutes(inputArray.minute);
	dDate.setSeconds(0);
	dDate.setMilliseconds(0);
	
	var iInterval =  now.getTime() - dDate.getTime();
	
	//alert(sDate + " - " + dDate + " - "+iInterval);
	
	if (iInterval < 60000)
	    iInterval = 0;
	
	document.getElementById("interval."+references.myDate.id).value = iInterval;
    }
    
    function getDateFromCalendarCloseMin(inputArray){
	var references = calendarObjForFormMin.getHtmlElementReferences();
	
	getDateFromCalendarClose(inputArray, references);
    }
    
    function getDateFromCalendarMin(inputArray){
	getDateFromCalendarCloseMin(inputArray);
	
	calendarObjForFormMin.hide();
    }

    function getDateFromCalendarCloseMax(inputArray){
	var references = calendarObjForFormMax.getHtmlElementReferences();
	
	getDateFromCalendarClose(inputArray, references);
    }
    
    function getDateFromCalendarMax(inputArray){
	getDateFromCalendarCloseMax(inputArray);
	
	calendarObjForFormMax.hide();
    }

    function showZero(value){
	return (value < 10 ? "0" : "") + value;
    }

    function dateToString(date){
	return date.getFullYear() + '-' + 
		showZero(date.getMonth()+1) + '-' + 
		showZero(date.getDate()) + ' ' + 
		showZero(date.getHours()) + ':' + 
		showZero(date.getMinutes()) ;
    }

    function init_form(){
	if(now.getFullYear()+"" == "NaN"){
	    now = new Date();
	    now.setMinutes(0);
	    now.setSeconds(0);
	    now.setMilliseconds(0);
	}
    
	var dmin = new Date(now.getTime()-document.getElementById("interval.min").value);
	var dmax = new Date(now.getTime()-document.getElementById("interval.max").value);
	
	document.getElementById("min").value = dateToString(dmin);
	document.getElementById("max").value = dateToString(dmax);
    
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

    function move_back(){
	var diff = document.getElementById("interval.max").value - document.getElementById("interval.min").value;
		
	if (diff<0)
    	    diff=-diff;

	if (diff<3600000)
    	    diff=3600000;
		    
	var x = document.getElementById("interval.max").value;
	document.getElementById("interval.max").value = eval(x+"+"+diff);
	
	var y = document.getElementById("interval.min").value;
	document.getElementById("interval.min").value = eval(y+"+"+diff);

	init_form();
	update_quick_jump();
		
	document.form1.submit();	
    }

    function move_next(){
	var diff = document.getElementById("interval.max").value - document.getElementById("interval.min").value;
		
	if (diff<0)
    	    diff=-diff;
		    
	if (diff<3600000)
    	    diff=3600000;
		    
	var x = document.getElementById("interval.max").value;
	x = eval(x+"-"+diff);
	
	if (x<0) x=0;
	
	document.getElementById("interval.max").value = x;
		
	var y = document.getElementById("interval.min").value;
	y = eval(y+"-"+diff);
	
	if (y<diff) y=diff;
	
	document.getElementById("interval.min").value = y;

	init_form();
	update_quick_jump();
		
	document.form1.submit();
    }
