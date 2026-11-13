package com.arny.aiprompts.platform

import com.arny.aiprompts.domain.interactors.AttachmentInput

expect fun pickChatAttachments(onPicked: (List<AttachmentInput>) -> Unit, onError: (String) -> Unit)
