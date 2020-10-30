# --- !Ups

CREATE OR REPLACE FUNCTION websearch_to_tsquery_postfix(dictionary REGCONFIG, query TEXT) RETURNS TSQUERY
    IMMUTABLE STRICT
    LANGUAGE plpgsql AS
$$
DECLARE
    arr  TEXT[]  := regexp_split_to_array(query, '\s+');;
    last TEXT    := websearch_to_tsquery(dictionary, arr[array_length(arr, 1)])::TEXT;;
    init TSQUERY := websearch_to_tsquery(dictionary, regexp_replace(query, '\S+$', ''));;
BEGIN
    RETURN init && to_tsquery(last || ':*');;
END;;
$$;

# --- !Downs

DROP FUNCTION websearch_to_tsquery_postfix;