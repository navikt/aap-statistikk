CREATE UNIQUE INDEX idx_person_id ON person (id);
CREATE UNIQUE INDEX idx_relaterte_personer_id ON relaterte_personer (id);
CREATE INDEX idx_relaterte_personer_person_id ON relaterte_personer (person_id);