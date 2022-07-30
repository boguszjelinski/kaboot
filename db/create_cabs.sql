--
-- PostgreSQL database dump
--

-- Dumped from database version 11.1
-- Dumped by pg_dump version 11.1

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: cab; Type: TABLE; Schema: public; Owner: kabina
--

CREATE TABLE public.cab (
    id bigint NOT NULL,
    location integer NOT NULL,
    status integer
);


ALTER TABLE public.cab OWNER TO kabina;

INSERT INTO cab (id, location, status) SELECT *, 0,2 FROM generate_series(0, 10000);


--
-- Name: cab cab_pkey; Type: CONSTRAINT; Schema: public; Owner: kabina
--

ALTER TABLE ONLY public.cab
    ADD CONSTRAINT cab_pkey PRIMARY KEY (id);


--
-- PostgreSQL database dump complete
--

