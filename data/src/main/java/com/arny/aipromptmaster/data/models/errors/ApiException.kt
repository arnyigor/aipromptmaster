package com.arny.aipromptmaster.data.models.errors

import com.arny.aipromptmaster.data.models.ApiError

class ApiException(apiError: ApiError) : Exception(apiError.message)