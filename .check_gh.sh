#!/bin/bash
echo 'session alive'
which gh
gh --version 2>&1
gh auth status 2>&1
gh run list --repo aqiyoung/openclaw --limit 3 --json number,status,conclusion 2>&1
