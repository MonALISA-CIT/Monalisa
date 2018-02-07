var TREE1_FORMAT =
[
//0. left position
	5,
//1. top position
	50,
//2. show +/- buttons
	true,
//3. couple of button images (collapsed/expanded/blank)
	["img/c.gif", "img/e.gif", "img/b.gif"],
//4. size of images (width, height,ident for nodes w/o children)
	[16,16,10],
//5. show folder image
	true,
//6. folder images (closed/opened/document)
	["img/folder.gif", "img/folderopen.gif", "img/page.gif"],
//7. size of images (width, height)
	[18,18],
//8. identation for each level [0/*first level*/, 16/*second*/, 32/*third*/,...]
	[0,15,30,45,60,75],
//9. tree background color ("" - transparent)
	"",
//10. default style for all nodes
	"clsNode",
//11. styles for each level of menu (default style will be used for undefined levels)
	[],
//12. true if only one branch can be opened at same time
	false,
//13. item pagging and spacing
	[0,0],
];
