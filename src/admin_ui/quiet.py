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
        if getattr(self, type):
            yield (self.start, 'start', self)
            if not self.held and not self.end_overlaps_other_period(type, all_periods):
                yield (self.end, 'end', self)

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
        sort_key=lambda e: (e[0], ['end', 'start'].index(e[1]))
        return sorted(itertools.chain(*(p.events(type, self.periods) for p in self.periods)))

    def latest_event(self, type, now, last):
        events = [e for e in self.events(type) if e[0] <= now and (e[0] > last if last else True)]
        return events[-1] if events else None

        
