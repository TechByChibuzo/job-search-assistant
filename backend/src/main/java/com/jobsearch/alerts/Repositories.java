package com.jobsearch.alerts;

import org.springframework.data.jpa.repository.JpaRepository;

interface GmailAccountRepository extends JpaRepository<GmailAccount, Long> {}

interface ImportedEmailRepository extends JpaRepository<ImportedEmail, String> {}
