.intel_syntax noprefix
.text
.globl ___chkstk_ms
___chkstk_ms:
    push rcx
    push rax
    cmp rax, 4096
    lea rcx, [rsp + 24]
    jb last
probe:
    sub rcx, 4096
    test dword ptr [rcx], eax
    sub rax, 4096
    cmp rax, 4096
    jae probe
last:
    sub rcx, rax
    test dword ptr [rcx], eax
    pop rax
    pop rcx
    ret
