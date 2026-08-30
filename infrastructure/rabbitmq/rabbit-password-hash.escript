#!/usr/bin/env escript
% 运行前提：仅接受显式 secret 文件路径，输出只能是密码哈希，不打印原始秘密。
% 破坏性边界：只读单个密码文件并生成哈希，不修改队列、卷、数据库或上传数据。
% 失败恢复：输入无效或哈希失败时返回非零码，不删除或覆盖源文件。
% 退出码：成功返回 0，参数或哈希失败返回非零码。

main([SecretPath]) ->
    {ok, RawPassword} = file:read_file(SecretPath),
    WithoutCr = binary:replace(RawPassword, <<13>>, <<>>, [global]),
    Password = binary:replace(WithoutCr, <<10>>, <<>>, [global]),
    true = byte_size(Password) > 0,
    Salt = crypto:strong_rand_bytes(4),
    Digest = crypto:hash(sha256, <<Salt/binary, Password/binary>>),
    io:format("~s", [base64:encode(<<Salt/binary, Digest/binary>>)]);
main(_) ->
    halt(64).
