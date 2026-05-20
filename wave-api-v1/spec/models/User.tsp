@doc("Wave USer details")
@example(#{
    id: 1,
    userName: "test",
    email: "test@seqera.io"
    })
model User {
  id: int64;
  userName: string;
  email: string;
}
