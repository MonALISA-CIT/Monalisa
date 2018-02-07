BLANK_IMAGE = "images/b.gif";

var STYLE = {
	border:1,			// item's border width, pixels; zero means "none"
	shadow:0,			// item's shadow size, pixels; zero means "none"
	color:{
		border:"#BBBBBB",	// color of the item border, if any
		shadow:"#F8F8F8",	// color of the item shadow, if any
		bgON:"#CCCCCC",		// background color for the items
		bgOVER:"#EEEEEE"	// background color for the item which is under mouse right now
	},
	css:{
		ON:"clsCMOn",		// CSS class for items
		OVER:"clsCMOver"	// CSS class  for item which is under mouse
	}
};

var STYLE_SELECTED = {
	border:1,			// item's border width, pixels; zero means "none"
	shadow:0,			// item's shadow size, pixels; zero means "none"
	color:{
		border:"#BBBBBB",	// color of the item border, if any
		shadow:"#F8F8F8",	// color of the item shadow, if any
		bgON:"#EBFFEB",		// background color for the items
		bgOVER:"#DBEEDB"	// background color for the item which is under mouse right now
	},
	css:{
		ON:"clsCMOn",		// CSS class for items
		OVER:"clsCMOver"	// CSS class  for item which is under mouse
	}
};

var NOSTYLE = {
	border:0,			// item's border width, pixels; zero means "none"
	shadow:0,			// item's shadow size, pixels; zero means "none"
	color:{
		border:"#BBBBBB",	// color of the item border, if any
		shadow:"#F8F8F8",	// color of the item shadow, if any
		bgON:"#CCCCCC",		// background color for the items
		bgOVER:"#EEEEEE"	// background color for the item which is under mouse right now
	},
	css:{
		ON:"clsTitleCMOn",		// CSS class for items
		OVER:"clsTitleCMOver"	// CSS class  for item which is under mouse
	}
};

var title_style = new Array();

for(i=0; i<4; i++){
    if(i == title_sort)
	title_style[i] = STYLE_SELECTED;
    else
	title_style[i] = STYLE;	 
}

var MENU_ITEMS = [
	{pos:'relative', itemoff:[0,99], leveloff:[21,0], style:NOSTYLE, size:[22,80],delay: 100},
	{code:"Status <img src=\"/img/down_arrow.png\" width=\"12\" height=\"12\"/>",
		sub:[
			{leveloff:[21,0], itemoff:[21,0], style: title_style[0]}, 
			{
			style: title_style[0],
			code:"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;All", ocode:"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;All",
			url:"/trend.jsp?filter=0"+sort_link
			},
			
			{
			style: title_style[1],			
			code:"<img src=\"/img/trend_ok.png\" width=\"16\" height=\"16\" align=\"left\"/>&nbsp;OK", ocode:"<img src=\"/img/trend_ok.png\" width=\"16\" height=\"16\"  align=\"left\"/>&nbsp;OK",
			url:"/trend.jsp?filter=1"+sort_link
			},
			
			{
			style: title_style[2],			
			code:"<img src=\"/img/trend_alert.png\" width=\"16\" height=\"16\" align=\"left\" />&nbsp;Warning", ocode:"<img src=\"/img/trend_alert.png\" width=\"16\" height=\"16\"  align=\"left\"/>&nbsp;Warning",
			url:"/trend.jsp?filter=2"+sort_link
				
			},
			{
			style: title_style[3],			
			code:"<img src=\"/img/trend_stop.png\" width=\"16\" height=\"16\"  align=\"left\"/>&nbsp;Stopped", ocode:"<img src=\"/img/trend_stop.png\" width=\"16\" height=\"16\"  align=\"left\"/>&nbsp;Stopped",
			url:"/trend.jsp?filter=3"+sort_link
			
			}
		]
	}
];
