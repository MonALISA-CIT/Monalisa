-- indexes to use in various cases
CREATE INDEX monitor_ids_key_1_idx ON monitor_ids(split_part(mi_key,'/',1));
CREATE INDEX monitor_ids_key_2_idx ON monitor_ids(split_part(mi_key,'/',2));
CREATE INDEX monitor_ids_key_3_idx ON monitor_ids(split_part(mi_key,'/',3));
CREATE INDEX monitor_ids_key_4_idx ON monitor_ids(split_part(mi_key,'/',4));
ALTER TABLE ONLY monitor_ids ALTER COLUMN mi_lastseen SET STATISTICS 1000;
ALTER TABLE ONLY monitor_ids ALTER COLUMN mi_lastnonzero SET STATISTICS 1000;
ANALYZE monitor_ids;

ALTER TABLE monitor_ids ALTER mi_lastseen SET DEFAULT 0;
UPDATE monitor_ids SET mi_lastseen=0 WHERE mi_lastseen IS NULL;

ALTER TABLE abping_aliases ADD COLUMN iconx int default 0;
ALTER TABLE abping_aliases ADD COLUMN icony int default 0;
ALTER TABLE abping_aliases ADD COLUMN labelx int default 7;
ALTER TABLE abping_aliases ADD COLUMN labely int default -15;
ALTER TABLE abping_aliases ADD COLUMN alias text;

-- get the greatest lastseen time for the given serice name
CREATE FUNCTION get_service_lastseen_time(text) RETURNS integer
    AS $_$
DECLARE
    site ALIAS FOR $1;
    retval integer;
BEGIN
    SELECT
        INTO retval
            mi_lastseen
        FROM
            monitor_ids
        WHERE
            split_part(mi_key,'/',1)=site
        ORDER BY
            mi_lastseen DESC
        LIMIT 1;
    RETURN retval;
END
$_$
    LANGUAGE plpgsql;


-- function to return the last online time of a service
-- the parameter is the service name
CREATE FUNCTION get_service_lastseen(text) RETURNS text
    AS $_$
    DECLARE
        site ALIAS FOR $1;
        retval text;
    BEGIN
        SELECT
            INTO retval
            split_part(date_trunc('hour', 'epoch'::timestamp+get_service_lastseen_time(site)*'1 second'::interval),':',1)||'h'
        ;
        RETURN retval;
    END
$_$
    LANGUAGE plpgsql;

CREATE TYPE monitoring_data AS (
        rectime integer,
        mval real,
        mmin real,
        mmax real
);

CREATE TYPE record_interval AS (
        tstart integer,
        tend integer
);

CREATE FUNCTION detect_holes(text, integer, integer, integer) RETURNS SETOF record_interval
    AS $_$
declare
    tablename alias for $1;
    mid       alias for $2;
    iinterval alias for $3;
    count     alias for $4;

    hole      int;

    lastcheck int;

    lastval   int;

    foundold  int;

    table_rec record;

    fresult   record_interval;

    tnow      int;
begin
    hole = iinterval * count;

    if hole<=0 then
        return;
    end if;

    select into table_rec ml_rectime from monitor_lastcheck where ml_tablename=tablename and ml_id=mid;

    if not found then
        --lastcheck := extract(epoch from now()-'1 day'::interval)::int;
        lastcheck := 0;
        foundold  := 0;
    else
        lastcheck := table_rec.ml_rectime;
        foundold  := 1;
    end if;

    lastval   := lastcheck;

    for table_rec in execute 'select rectime from ' || tablename || ' where rectime>' || lastcheck || ' and id=' || mid || ' order by rectime asc;' loop
        if table_rec.rectime-lastval > hole and table_rec.rectime-lastval>2*iinterval then
            fresult.tstart := lastval+iinterval;
            fresult.tend   := table_rec.rectime-iinterval;

            return next fresult;
        end if;

        lastval := table_rec.rectime;
    end loop;

    tnow = extract(epoch from now())::int;

    if lastval>0 and tnow-lastval>hole and tnow-lastval>2*iinterval then
        fresult.tstart := lastval+iinterval;
        fresult.tend   := tnow-iinterval;

        return next fresult;
    end if;

    if lastval<=0 then
        lastval := tnow;
    end if;

    if foundold!=0 then
        update monitor_lastcheck set ml_rectime=lastval where ml_tablename=tablename and ml_id=mid;
    else
        insert into monitor_lastcheck (ml_tablename, ml_id, ml_rectime) values (tablename, mid, lastval);
    end if;

    return;
end;
$_$
    LANGUAGE plpgsql;

CREATE FUNCTION get_lastseen(text) RETURNS text
    AS $_$
DECLARE
    site ALIAS FOR $1;
    retval text;
BEGIN
    SELECT
        INTO retval
            split_part(date_trunc('hour', 'epoch'::timestamp+mi_lastseen*'1 second'::interval),':',1)||'h'
        FROM
            monitor_ids
        WHERE
            split_part(mi_key,'/',1)=site
        ORDER BY
            mi_lastseen DESC
        LIMIT 1;

    RETURN retval;
    END
$_$
    LANGUAGE plpgsql STABLE;

CREATE FUNCTION get_predicate_uptime(text, integer) RETURNS real
    AS $_$
    DECLARE
        predicate ALIAS FOR $1;
        totaltime ALIAS FOR $2;
        key_id int;

        curs1 refcursor;

        values_count int;
        values_avg real;

        retval real;
    BEGIN
        SELECT mi_id INTO key_id FROM monitor_ids WHERE mi_key=predicate;

        IF NOT FOUND THEN
            return null;
        END IF;

        OPEN curs1 FOR
        SELECT
            count(1),
            avg(mval)
        FROM
            w4_1m_status
        WHERE
            id=key_id AND
            rectime>extract(epoch FROM now())::int-totaltime
        ;

        FETCH curs1 INTO values_count, values_avg;

        retval := (1-values_avg)*(values_count/(totaltime/120.0-1));

        IF retval>1 THEN
            retval := 1;
        END IF;

        RETURN retval;
    END;

$_$
    LANGUAGE plpgsql;

CREATE FUNCTION max(integer, integer) RETURNS integer
    AS $_$
    BEGIN
        IF $1 > $2 THEN
            return $1;
        ELSE
            return $2;
        END IF;
    END;
$_$
    LANGUAGE plpgsql STABLE;

CREATE FUNCTION reverse_date(text) RETURNS text
    AS $_$
declare
    d_in alias for $1;
begin
    return split_part(d_in,'-',3)||'.'||split_part(d_in,'-',2)||'.'||split_part(d_in,'-',1);
end
$_$
    LANGUAGE plpgsql STABLE;

CREATE TABLE hidden_sites_extra (
    name text NOT NULL
);


CREATE VIEW googlemap AS
    SELECT 
	abping_aliases.geo_lat,
	abping_aliases.geo_long,
	abping_aliases.name,
	abping_aliases.iconx,
	abping_aliases.icony,
	abping_aliases.labelx,
	abping_aliases.labely,
	(SELECT 
		(((date_part('epoch'::text, now()) - (monitor_ids.mi_lastseen)::double precision) / (3600)::double precision))::integer AS int4 
	  FROM ONLY 
		monitor_ids 
	  WHERE 
		((monitor_ids.mi_key)::text = (('CERN/ALICE_Sites_Jobs_Summary/'::text || (abping_aliases.name)::text) || '/RUNNING_jobs'::text))
	) AS lastseen, 
	(SELECT 
		monitor_ids.mi_lastvalue 
	  FROM ONLY 
		monitor_ids
	  WHERE 
		((monitor_ids.mi_key)::text = (('CERN/ALICE_Sites_Jobs_Summary/'::text || (abping_aliases.name)::text) || '/RUNNING_jobs'::text))
	) AS lastvalue,
	CASE WHEN (abping_aliases.alias IS NULL) THEN (abping_aliases.name)::text ELSE abping_aliases.alias END AS alias
    FROM ONLY 
	abping_aliases
    WHERE 
	(((
		(((abping_aliases.geo_lat IS NOT NULL) AND (abping_aliases.geo_long IS NOT NULL)) AND ((abping_aliases.geo_lat)::text <> 'N/A'::text)) 
		AND ((abping_aliases.geo_long)::text <> 'N/A'::text))
		AND (NOT ((abping_aliases.name)::text IN (SELECT hidden_sites.name FROM ONLY hidden_sites))))
		AND (NOT ((abping_aliases.name)::text IN (SELECT hidden_sites_extra.name FROM ONLY hidden_sites_extra))))
    ORDER BY 
	(SELECT 
		(((date_part('epoch'::text, now()) - (monitor_ids.mi_lastseen)::double precision) / (3600)::double precision))::integer AS int4 
	FROM ONLY 
		monitor_ids 
	WHERE 
		((monitor_ids.mi_key)::text = (('CERN/ALICE_Sites_Jobs_Summary/'::text || (abping_aliases.name)::text) || '/RUNNING_jobs'::text))) DESC, 
	(SELECT 
		monitor_ids.mi_lastvalue
	FROM ONLY 
		monitor_ids
	WHERE 
		((monitor_ids.mi_key)::text = (('CERN/ALICE_Sites_Jobs_Summary/'::text || (abping_aliases.name)::text) || '/RUNNING_jobs'::text)));

CREATE TABLE site_relations (
    site text,
    connectedto text,
    centertype integer,
    color text
);

CREATE VIEW site_relations_googlemap AS
    SELECT 
	src.geo_lat AS src_lat,
	src.geo_long AS src_long,
	dest.geo_lat AS dest_lat,
	dest.geo_long AS dest_long,
	site_relations.color
    FROM 
	((ONLY site_relations JOIN ONLY abping_aliases src ON ((site_relations.site = (src.name)::text))) JOIN ONLY abping_aliases dest ON ((site_relations.connectedto = (dest.name)::text)));

CREATE TABLE subscribers (
    s_id integer NOT NULL,
    s_email text NOT NULL,
    s_name text NOT NULL,
    s_agid integer NOT NULL,
    s_site text NOT NULL,
    s_code text NOT NULL,
    s_valid smallint DEFAULT 0 NOT NULL,
    s_date timestamp without time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE subscribers_s_id_seq
    INCREMENT BY 1
    NO MAXVALUE
    NO MINVALUE
    CACHE 1;

ALTER SEQUENCE subscribers_s_id_seq OWNED BY subscribers.s_id;

ALTER TABLE subscribers ALTER COLUMN s_id SET DEFAULT nextval('subscribers_s_id_seq'::regclass);

ALTER TABLE ONLY subscribers
    ADD CONSTRAINT subscribers_s_agid_fkey FOREIGN KEY (s_agid) REFERENCES annotation_groups(ag_id);

CREATE TABLE temp_assoc (
    site text NOT NULL,
    chart integer NOT NULL,
    sensor text NOT NULL
);

CREATE UNIQUE INDEX temp_assoc_pkey ON temp_assoc USING btree (site, chart);

ALTER TABLE ONLY hidden_sites_extra
    ADD CONSTRAINT hidden_sites_extra_pkey PRIMARY KEY (name);

ALTER TABLE ONLY subscribers
    ADD CONSTRAINT subscribers_pkey PRIMARY KEY (s_id);

ALTER TABLE ONLY subscribers
    ADD CONSTRAINT subscribers_s_email_key UNIQUE (s_email, s_agid, s_site);

CREATE TABLE shorturl (
    id bigint primary key,
    path text unique,
    addtime integer DEFAULT (date_part('epoch'::text, now()))::integer,
    requestcount integer DEFAULT 1,
    lastrequested integer DEFAULT (date_part('epoch'::text, now()))::integer,
    accesscount integer DEFAULT 0,
    lastaccessed integer
);
