import { useEffect } from "react";
import type { InfiniteData, UseInfiniteQueryResult } from "@tanstack/react-query";
import * as api from "@/api/generated/api";

const paging = {
  initialPageParam: 1,
  getNextPageParam: (last: { pageInfo: api.PageInfo }) =>
    last.pageInfo.page < last.pageInfo.totalPages ? last.pageInfo.page + 1 : undefined,
};

// Small reference lists and dashboard totals need every server page, including
// records after the first 100. Keep using the generated, abortable API hooks.
function useAllPages<P extends { content: unknown[]; pageInfo: api.PageInfo }>(
  query: UseInfiniteQueryResult<InfiniteData<P, number | undefined>, unknown>,
) {
  const { hasNextPage, isFetching, isError, fetchNextPage } = query;
  useEffect(() => {
    if (hasNextPage && !isFetching && !isError) void fetchNextPage();
  }, [hasNextPage, isFetching, isError, fetchNextPage]);
  const content = query.data?.pages.flatMap((page) => page.content) as P["content"] | undefined;
  const complete = !hasNextPage && !query.isFetchingNextPage;
  return {
    ...query,
    isPending: query.isPending || (!isError && !complete),
    data:
      query.data && complete
        ? {
            ...query.data.pages[0],
            content: content!,
            pageInfo: {
              ...query.data.pages[0].pageInfo,
              page: 1,
              totalPages: 1,
              size: content!.length,
            },
          }
        : undefined,
  };
}

export function useAllLessons(params: api.GetLessonsParams = {}, enabled = true) {
  return useAllPages<api.LessonPageResponse>(
    api.useGetLessonsInfinite<InfiniteData<api.LessonPageResponse, number | undefined>>(
      { ...params, size: 100 },
      { query: { ...paging, enabled } },
    ),
  );
}

export function useAllReservations(params: api.GetReservationsParams = {}, enabled = true) {
  return useAllPages<api.ReservationPageResponse>(
    api.useGetReservationsInfinite<InfiniteData<api.ReservationPageResponse, number | undefined>>(
      { ...params, size: 100 },
      { query: { ...paging, enabled } },
    ),
  );
}

export function useAllAbsences(params: api.GetAbsencesParams = {}, enabled = true) {
  return useAllPages<api.AbsencePageResponse>(
    api.useGetAbsencesInfinite<InfiniteData<api.AbsencePageResponse, number | undefined>>(
      { ...params, size: 100 },
      { query: { ...paging, enabled } },
    ),
  );
}

export function useAllMembers(params: api.GetMembersParams = {}, enabled = true) {
  return useAllPages<api.MemberPageResponse>(
    api.useGetMembersInfinite<InfiniteData<api.MemberPageResponse, number | undefined>>(
      { ...params, size: 100 },
      { query: { ...paging, enabled } },
    ),
  );
}

export function useAllInstructors(params: api.GetInstructorsParams = {}, enabled = true) {
  return useAllPages<api.InstructorPageResponse>(
    api.useGetInstructorsInfinite<InfiniteData<api.InstructorPageResponse, number | undefined>>(
      { ...params, size: 100 },
      { query: { ...paging, enabled } },
    ),
  );
}

export function useAllPassProducts(params: api.GetPassProductsParams = {}, enabled = true) {
  return useAllPages<api.PassProductPageResponse>(
    api.useGetPassProductsInfinite<InfiniteData<api.PassProductPageResponse, number | undefined>>(
      { ...params, size: 100 },
      { query: { ...paging, enabled } },
    ),
  );
}

export function useAllLessonTypes(params: api.GetLessonTypesParams = {}, enabled = true) {
  return useAllPages<api.LessonTypePageResponse>(
    api.useGetLessonTypesInfinite<InfiniteData<api.LessonTypePageResponse, number | undefined>>(
      { ...params, size: 100 },
      { query: { ...paging, enabled } },
    ),
  );
}

export function useAllRooms(params: api.GetRoomsParams = {}, enabled = true) {
  return useAllPages<api.RoomPageResponse>(
    api.useGetRoomsInfinite<InfiniteData<api.RoomPageResponse, number | undefined>>(
      { ...params, size: 100 },
      { query: { ...paging, enabled } },
    ),
  );
}

export function useAllPayments(params: api.GetPaymentsParams = {}, enabled = true) {
  return useAllPages<api.PassPaymentPageResponse>(
    api.useGetPaymentsInfinite<InfiniteData<api.PassPaymentPageResponse, number | undefined>>(
      { ...params, size: 100 },
      { query: { ...paging, enabled } },
    ),
  );
}
