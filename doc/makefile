
all: html pdf

pdf: cli_pdf ser_pdf ws_pdf

html: cli_html ser_html ws_html

## HTML ################

ws_html : ml_ws_ug.xml
	make ml_ws_ug.html

ser_html : ml_ser_ug.xml
	make ml_ser_ug.html
	sed s/target=\"_top\"\>ApMon/target=\"frm_main\"\>ApMon/ ml_ser_ug.html > ml_ser_ug.html.tmp
	mv ml_ser_ug.html.tmp ml_ser_ug.html

cli_html : ml_cli_ug.xml
	make ml_cli_ug.html

## PDF #################

ws_pdf : ml_ws_ug.xml
	make ml_ws_ug.pdf

ser_pdf : ml_ser_ug.xml 
	make ml_ser_ug.pdf

cli_pdf : ml_cli_ug.xml 
	make ml_cli_ug.pdf

#######################

%.html : %.xml
	xsltproc --novalid -o $@ html.xsl $<
	sed -i -e "s/\xC2\xA9/\&#169;/g" $@
	sed -i -e "s/\xC2\xA0/ /g" $@
	sed -i -e "s/\xE2\x80\x9C/\&#147;/g" $@
	sed -i -e "s/\xE2\x80\x9D/\&#148;/g" $@
	tidy -i -m $@ ; true			

%.pdf : %.xml 
	xsltproc --novalid -o `basename $< .xml`.tex print.xsl `basename $< .xml`.xml
	latex `basename $< .xml`.tex ; true
	latex `basename $< .xml`.tex ; true
	dvipdf `basename $< .xml`.dvi

#######################

clean2: clean 
	rm *.pdf *.html

clean:
	rm -f *.tex *.aux *.dvi *.glo *.idx *.log *.out *~

