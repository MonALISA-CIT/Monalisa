<script>
function submitfilterAnnotations(sort, sort_value){
    document.forms.filterAnnotations.sort.value = sort;
    document.forms.filterAnnotations.sortvalue.value = (sort_value.length == 0) ? 'desc' : sort_value;
    document.forms.filterAnnotations.submit();
    return false;
}
</script>
<form name="filterAnnotations" action="<<:jsp:>>" method="POST">
<input type="hidden" name="sort" value="<<:sort:>>">
<input type="hidden" name="sortvalue" value="<<:sortvalue:>>">
<tr bgcolor="#CCCCCC">
    <th>ID</td>
    <td align="center"><a class="link" onclick="return submitfilterAnnotations(6, '<<:sort_6:>>')" href="javascript: void(0);">Category</a><<:img_6:>></td>
    <td align="center"><a class="link" onclick="return submitfilterAnnotations(1, '<<:sort_1:>>')" href="javascript: void(0);">Message</a><<:img_1:>></td>
    <td align="center"><a class="link" onclick="return submitfilterAnnotations(2, '<<:sort_2:>>')" href="javascript: void(0);">Applies to</a><<:img_2:>></td>
    <td align="center"><a class="link" onclick="return submitfilterAnnotations(3, '<<:sort_3:>>')" href="javascript: void(0);">Duration</a><<:img_3:>></td>
    <td align="center"><a class="link" onclick="return submitfilterAnnotations(4, '<<:sort_4:>>')" href="javascript: void(0);">Start</a><<:img_4:>></td>
    <td align="center"><a class="link" onclick="return submitfilterAnnotations(5, '<<:sort_5:>>')" href="javascript: void(0);">End</a><<:img_5:>></td>
    <th>Options</td>
</tr>
<tr bgcolor="#FFFFFF">
    <td></td>
    <td><select class="input_select" name="groups" onchange="document.forms.filterAnnotations.submit();">
	    <option value="">All</option>
	    <<:groups_options:>>
	</select>
    </td>
    <td><input type="text" name="filter_1" value="<<:filter_1:>>" class="input_text"></td>   
    <td><input type="text" name="filter_2" value="<<:filter_2:>>" class="input_text"></td>
    <td>
    </td>
    <td>
	<select name="filter_4" class="input_select" onchange="document.forms.filterAnnotations.submit();">
	    <option value="1" <<:filter_4_1:>>>Last Day</option>	    	
	    <option value="7" <<:filter_4_7:>>>Last Week</option>
	    <option value="30" <<:filter_4_30:>>>Last Month</option>	    
	    <option value="365" <<:filter_4_365:>>>Last Year</option>
	    <option value="-1" <<:filter_4_-1:>>>All</option>	    	    
	</select>
    </td>
    <td>
	<select name="filter_5" class="input_select" onchange="document.forms.filterAnnotations.submit();">
	    <option value="0" <<:filter_5_0:>>>All</option>
	    <option value="1" <<:filter_5_1:>>>Continues</option>	    
	</select>
    </td>
    <td><input type="submit" name="buton" value="Submit" class="input_submit"></td>
</tr>
</form>
