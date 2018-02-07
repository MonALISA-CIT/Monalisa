ALTER TABLE abping_aliases ADD COLUMN labelx int default 5;
ALTER TABLE abping_aliases ADD COLUMN labely int default -25;
ALTER TABLE abping_aliases ADD COLUMN iconx int default 0;
ALTER TABLE abping_aliases ADD COLUMN icony int default 0;

CREATE TABLE site_relations (
    site	text,
    connectedto	text,
    centertype	int,
    color	text
);

CREATE VIEW site_relations_googlemap AS
SELECT
    src.geo_lat as src_lat,
    src.geo_long as src_long,
    dest.geo_lat as dest_lat,
    dest.geo_long as dest_long,
    color
FROM
    (site_relations INNER JOIN abping_aliases src ON site=src.name) INNER JOIN abping_aliases dest ON connectedto=dest.name
;

CREATE TABLE hidden_sites (name text);
CREATE TABLE hidden_sites_extra (name text);

SELECT abping_aliases.geo_lat, 
       abping_aliases.geo_long, 
       abping_aliases.name, 
       abping_aliases.iconx, 
       abping_aliases.icony, 
       abping_aliases.labelx, 
       abping_aliases.labely, 
       (
        SELECT ((date_part('epoch'::text, now()) - monitor_ids.mi_lastseen::double precision) / 3600::double precision)::integer AS int4
	    FROM ONLY monitor_ids
	    WHERE 
		monitor_ids.mi_key::text = (('CERN/ALICE_Sites_Jobs_Summary/'::text || abping_aliases.name::text) || '/RUNNING_jobs'::text)
       ) AS lastseen, 
       
       ( SELECT monitor_ids.mi_lastvalue
            FROM ONLY monitor_ids
            WHERE monitor_ids.mi_key::text = (('CERN/ALICE_Sites_Jobs_Summary/'::text || abping_aliases.name::text) || '/RUNNING_jobs'::text)
       ) AS lastvalue,
       CASE
           WHEN abping_aliases.alias IS NULL THEN abping_aliases.name::text
           ELSE abping_aliases.alias
       END AS alias
    FROM ONLY abping_aliases
    WHERE 
	abping_aliases.geo_lat IS NOT NULL AND 
	abping_aliases.geo_long IS NOT NULL AND 
	abping_aliases.geo_lat::text <> 'N/A'::text AND 
	abping_aliases.geo_long::text <> 'N/A'::text AND 
	NOT (abping_aliases.name::text IN ( SELECT hidden_sites.name FROM ONLY hidden_sites)) AND 
	NOT (abping_aliases.name::text IN ( SELECT hidden_sites_extra.name FROM ONLY hidden_sites_extra))
    ORDER BY 
	( SELECT ((date_part('epoch'::text, now()) - monitor_ids.mi_lastseen::double precision) / 3600::double precision)::integer AS int4 FROM ONLY monitor_ids                                                                                                                                     WHERE monitor_ids.mi_key::text = (('CERN/ALICE_Sites_Jobs_Summary/'::text || abping_aliases.name::text) || '/RUNNING_jobs'::text)) DESC, 
	( SELECT monitor_ids.mi_lastvalue FROM ONLY monitor_ids WHERE monitor_ids.mi_key::text = (('CERN/ALICE_Sites_Jobs_Summary/'::text || abping_aliases.name::text) || '/RUNNING_jobs'::text));
                                                                                                                                                          