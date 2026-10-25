-- What a posting says about visas and legal status, read from its description, with the sentence as evidence
alter table job add column visa_restriction text;
alter table job add column visa_evidence text;

-- The owner can mark a company as not sponsoring visas, for roles whose postings don't say
alter table company add column no_sponsorship boolean not null default false;
