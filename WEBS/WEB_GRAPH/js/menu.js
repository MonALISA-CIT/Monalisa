function showMenu(){
	dMLMenu = new dTree('dMLMenu');
	
	dMLMenu.add(0,-1,'MonALISA Repository','/show?page=index.html');
		dMLMenu.add(1,0,'Global Views','');
			dMLMenu.add(10,1,'Interactive Map','/show?page=index.html');
			dMLMenu.add(11,1,'Load 5 - Spider','/display?page=spider');
			dMLMenu.add(12,1,'Farms Usage','/display?page=spider_usage');

		dMLMenu.add(2,0,'Statistics','');
			dMLMenu.add(20,2,'Farms','/stats?page=stats2_2');
			dMLMenu.add(21,2,'Traffic','/stats?page=stats2_3');
			dMLMenu.add(22,2,'Farms Load','/stats?page=stats_usage');

		dMLMenu.add(3,0,'Services','/display?page=spider');
			dMLMenu.add(30,3,'Load','');
				dMLMenu.add(300,30,'Real-Time','/display?page=rt_link2');
				dMLMenu.add(301,30,'History','/display?page=hist_link2');
			dMLMenu.add(31,3,'Traffic','display?/page=eth');
				dMLMenu.add(310,31,'eth0','');
					dMLMenu.add(3100,310,'Real-Time','/display?page=rt_link5');
					dMLMenu.add(3101,310,'History','/display?page=hist_link5_com');
				dMLMenu.add(311,31,'eth1','');
					dMLMenu.add(3110,311,'Real-Time','/display?page=rt_link6');
					dMLMenu.add(3111,311,'History','/display?page=hist_link6_com');

		dMLMenu.add(4,0,'Sites status','/stats?page=summary');
		
		dMLMenu.add(9, 0, 'AF');
		    dMLMenu.add(91, 9, 'Status table', '/stats?page=AF/table');
		    dMLMenu.add(92, 9, 'Parameter history', '/stats?page=AF/parameter');
		    dMLMenu.add(93, 9, 'Machine history', '/stats?page=AF/machine');
                    dMLMenu.add(94, 9, 'Data management', '/stats?page=AF/xrddm');
		    		
		dMLMenu.add(5,0,'Repository info','/info.jsp');
				
	document.write(dMLMenu);
}
