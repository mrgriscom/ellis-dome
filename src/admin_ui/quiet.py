import itertools

# this class is use in settings, so must be in a distinct file with no project dependencies to avoid import loops

class GoDark(object):
    def __init__(self, start, duration, just_audio=False, name=None):
        self.start = start
        self.duration = duration
        self.end = start + duration
        self.audio = True
        self.visual = not just_audio
        self.name = name
        self.held = False

    def within(self, dt):
        return self.start <= dt and dt < self.end

    def expired(self, dt):
        return dt >= self.end
    
    def events(self, type, all_periods):
        def _event(which, status):
            return {'time': getattr(self, which), 'type': which, 'period': self, 'status': status}
        
        if getattr(self, type):
            yield _event('start', 'active')
            end_status = 'active'
            if self.held:
                end_status = 'hold'
            elif self.end_overlaps_other_period(type, all_periods):
                end_status = 'overlapping period'
            yield _event('end', end_status)

    def end_overlaps_other_period(self, type, all_periods):
        assert getattr(self, type)
        other_periods = [p for p in all_periods if p != self and getattr(p, type)]
        return any(p.within(self.end) for p in other_periods)

    def param(self, manager):
        import animations  # avoid import loop
        return animations.QuietPeriodParameter(manager, self)
    
class GoDarkSet(object):
    def __init__(self, periods):
        self.periods = periods

    def events(self, type):
        # ensure in case of stard/end at same instant, prev period's end event comes before next period's start event
        sort_key=lambda e: (e['time'], ['end', 'start'].index(e['type']))
        return sorted(itertools.chain(*(p.events(type, self.periods) for p in self.periods)), key=sort_key)

    # return most recent event to process (excluding events that should be skipped), also return the most recent skipped event if it that is more recent
    def latest_event(self, type, now, last):
        def time_filter(evs):
            filtered = [e for e in evs if e['time'] <= now and (e['time'] > last if last else True)]
            return filtered[-1] if filtered else None
        events = self.events(type)
        latest = time_filter(events)
        latest_active = time_filter([e for e in events if e['status'] == 'active'])
        return latest_active, (latest if latest != latest_active else None)

        
