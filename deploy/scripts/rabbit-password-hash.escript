#!/usr/bin/env escript

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
