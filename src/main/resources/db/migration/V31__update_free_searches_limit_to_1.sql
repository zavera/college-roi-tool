-- Drop the free-search cap from 3 to 1 per tab before the paywall shows.
UPDATE app_config SET config_value = '1' WHERE config_key = 'free_searches_limit';
