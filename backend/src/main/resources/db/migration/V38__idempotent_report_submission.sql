ALTER TABLE report_submissions ADD COLUMN submit_operation_key TEXT;

CREATE UNIQUE INDEX uq_report_submission_submit_operation_key
    ON report_submissions(submit_operation_key)
    WHERE submit_operation_key IS NOT NULL;
