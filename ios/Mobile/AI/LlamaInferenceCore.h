// -*- Mode: C; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#pragma once

#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

bool xl_llama_is_available(void);

bool xl_llama_load_model(const char *path, int contextSize, int threads);

void xl_llama_unload_model(void);

bool xl_llama_prefill_messages(const char **roles, const char **contents, int count);

/** 0 = stop (EOG/error), 1 = token written to buf, 2 = pending (incomplete UTF-8, buf empty). */
int xl_llama_sample_token(char *buf, size_t buf_len);

#ifdef __cplusplus
}
#endif
