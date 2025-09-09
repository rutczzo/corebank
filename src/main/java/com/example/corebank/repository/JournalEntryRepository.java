package com.example.corebank.repository;
import com.example.corebank.domain.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {}
