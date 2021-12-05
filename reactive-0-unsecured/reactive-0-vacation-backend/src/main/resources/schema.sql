drop table vacations if exists;
create table vacations(
    id bigserial primary key,
    username varchar(255) not null,
    date_from date not null,
    date_to date not null,
    hours bigint not null,
    status varchar(255) not null
);
