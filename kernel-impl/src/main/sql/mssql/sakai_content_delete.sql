-----------------------------------------------------------------------------
-- CONTENT_RESOURCE_DELETE
-- TODO: add CONTENT_RESOURCE_BODY_BINARY_DELETE table if required
-----------------------------------------------------------------------------

CREATE TABLE CONTENT_RESOURCE_DELETE
(
    RESOURCE_ID NVARCHAR (255) NOT NULL,
    RESOURCE_UUID NVARCHAR (36),
	IN_COLLECTION NVARCHAR (255),
	FILE_PATH NVARCHAR (128),
    RESOURCE_TYPE_ID NVARCHAR (255),
	DELETE_DATE SMALLDATETIME,
	DELETE_USERID NVARCHAR (36),
    XML NVARCHAR(MAX),
    BINARY_ENTITY VARBINARY(MAX) 
    
)
;
sp_tableoption 'CONTENT_RESOURCE_DELETE', 'large value types out of row', 'true'
;

CREATE UNIQUE INDEX CONTENT_RESOURCE_UUID_DELETE_I ON CONTENT_RESOURCE_DELETE
(
	RESOURCE_UUID
)

CREATE INDEX CONTENT_RESOURCE_DELETE_INDEX ON CONTENT_RESOURCE_DELETE
(
	RESOURCE_ID
)
;

-----------------------------------------------------------------------------
-- CONTENT_RESOURCE_DELETE_BB
-----------------------------------------------------------------------------

CREATE TABLE CONTENT_RESOURCE_DELETE_BB
(
    RESOURCE_ID VARCHAR (255) NOT NULL,
    BODY VARBINARY(MAX)
);

sp_tableoption 'CONTENT_RESOURCE_DELETE_BB', 'large value types out of row', 'true'
;

CREATE UNIQUE INDEX CONTENT_RESOURCE_DELETE_BB_INDEX ON CONTENT_RESOURCE_DELETE_BB
(
	RESOURCE_ID
)
;